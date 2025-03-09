package net.nevq.nevformance.metrics.collectors;

import net.minecraft.server.MinecraftServer;
import net.nevq.nevformance.metrics.MetricsManager;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Specialized collector for system resource metrics (CPU, memory, GC, etc.)
 */
public class SystemMetricsCollector implements MetricCollector {

    // JVM management beans
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();

    // Track GC activity
    private final Map<String, Long> lastGcCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> lastGcTimes = new ConcurrentHashMap<>();

    // Track thread CPU usage
    private final Map<Long, Long> lastThreadCpuTimes = new ConcurrentHashMap<>();

    // Tracking tick time statistics
    private final List<Double> recentTickTimes = new ArrayList<>();
    private static final int TICK_TIMES_WINDOW = 600; // 30 seconds at 20 TPS

    // Lag spike detection
    private static final double LAG_SPIKE_THRESHOLD_MS = 100.0; // 100ms = 5% of a tick at 20 TPS
    private final Queue<LagSpike> recentLagSpikes = new ArrayDeque<>();
    private static final int MAX_LAG_SPIKES = 50;

    @Override
    public void collect(MetricsManager manager, MinecraftServer server, long timestamp) {
        // Memory metrics
        collectMemoryMetrics(manager, timestamp);

        // CPU metrics
        collectCpuMetrics(manager, timestamp);

        // Thread metrics
        collectThreadMetrics(manager, timestamp);

        // Garbage collection metrics
        collectGcMetrics(manager, timestamp);

        // Tick timing analysis
        collectTickMetrics(manager, server, timestamp);
    }

    /**
     * Collects memory-related metrics
     */
    private void collectMemoryMetrics(MetricsManager manager, long timestamp) {
        // Heap memory
        long usedHeapMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
        long committedHeapMemory = memoryBean.getHeapMemoryUsage().getCommitted() / (1024 * 1024); // MB
        long maxHeapMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024); // MB

        // Non-heap memory (metaspace, etc.)
        long usedNonHeapMemory = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024); // MB

        // Record metrics
        manager.recordMetric("memory.heap.used", timestamp, usedHeapMemory);
        manager.recordMetric("memory.heap.committed", timestamp, committedHeapMemory);
        manager.recordMetric("memory.heap.max", timestamp, maxHeapMemory);
        manager.recordMetric("memory.nonheap.used", timestamp, usedNonHeapMemory);

        // Calculate heap utilization percentage
        double heapUtilizationPct = (double) usedHeapMemory / maxHeapMemory * 100.0;
        manager.recordMetric("memory.heap.utilization", timestamp, heapUtilizationPct);

        // Memory pools - more detailed breakdown
        for (MemoryPoolMXBean memoryPool : ManagementFactory.getMemoryPoolMXBeans()) {
            String poolName = memoryPool.getName().replace(" ", "_").toLowerCase();
            long usedMemory = memoryPool.getUsage().getUsed() / (1024 * 1024); // MB

            manager.recordMetric("memory.pools." + poolName, timestamp, usedMemory);
        }
    }

    /**
     * Collects CPU-related metrics
     */
    private void collectCpuMetrics(MetricsManager manager, long timestamp) {
        // System CPU metrics
        // Note: some of these are only available with com.sun APIs
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;

            double systemCpuLoad = sunOsBean.getCpuLoad() * 100.0;
            double processCpuLoad = sunOsBean.getProcessCpuLoad() * 100.0;

            manager.recordMetric("cpu.system", timestamp, systemCpuLoad);
            manager.recordMetric("cpu.process", timestamp, processCpuLoad);
        }

        // Available processors
        int availableProcessors = osBean.getAvailableProcessors();
        manager.recordMetric("cpu.available_processors", timestamp, availableProcessors);

        // System load average (from standard API)
        double systemLoadAverage = osBean.getSystemLoadAverage();
        if (systemLoadAverage >= 0) { // May not be available on all platforms
            manager.recordMetric("cpu.system_load_average", timestamp, systemLoadAverage);
        }
    }

    /**
     * Collects thread-related metrics
     */
    private void collectThreadMetrics(MetricsManager manager, long timestamp) {
        // Basic thread stats
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        long totalStartedThreadCount = threadBean.getTotalStartedThreadCount();

        manager.recordMetric("threads.active", timestamp, threadCount);
        manager.recordMetric("threads.peak", timestamp, peakThreadCount);

        // Thread state distribution
        Map<Thread.State, Integer> threadStateCount = new HashMap<>();
        for (Thread.State state : Thread.State.values()) {
            threadStateCount.put(state, 0);
        }

        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadBean.getAllThreadIds());
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                threadStateCount.merge(info.getThreadState(), 1, Integer::sum);
            }
        }

        // Record thread states
        for (Map.Entry<Thread.State, Integer> entry : threadStateCount.entrySet()) {
            manager.recordMetric(
                    "threads.states." + entry.getKey().name().toLowerCase(),
                    timestamp,
                    entry.getValue()
            );
        }

        // Analysis of thread CPU time (if supported)
        if (threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled()) {
            // Get current CPU times
            Map<Long, Long> currentThreadCpuTimes = new HashMap<>();
            Map<String, Long> threadTimeDiffs = new HashMap<>();
            AtomicLong totalCpuTime = new AtomicLong(0);

            for (long threadId : threadBean.getAllThreadIds()) {
                long cpuTime = threadBean.getThreadCpuTime(threadId);
                if (cpuTime > 0) {
                    currentThreadCpuTimes.put(threadId, cpuTime);

                    // Calculate CPU time diff since last measurement
                    if (lastThreadCpuTimes.containsKey(threadId)) {
                        long lastTime = lastThreadCpuTimes.get(threadId);
                        long timeDiff = cpuTime - lastTime;

                        if (timeDiff > 0) {
                            // Get thread name
                            ThreadInfo info = threadBean.getThreadInfo(threadId);
                            if (info != null) {
                                String threadName = info.getThreadName();

                                // Categorize server threads
                                String category = categorizeThread(threadName);
                                threadTimeDiffs.merge(category, timeDiff, Long::sum);
                                totalCpuTime.addAndGet(timeDiff);
                            }
                        }
                    }
                }
            }

            // Update last CPU times
            lastThreadCpuTimes.clear();
            lastThreadCpuTimes.putAll(currentThreadCpuTimes);

            // Record CPU time distribution by thread category
            if (totalCpuTime.get() > 0) {
                for (Map.Entry<String, Long> entry : threadTimeDiffs.entrySet()) {
                    double percentage = (double) entry.getValue() / totalCpuTime.get() * 100.0;
                    manager.recordMetric(
                            "threads.cpu." + entry.getKey(),
                            timestamp,
                            percentage
                    );
                }
            }
        }
    }

    /**
     * Categorizes a thread by its name into a functional category
     */
    private String categorizeThread(String threadName) {
        if (threadName.contains("Server thread")) {
            return "main";
        } else if (threadName.contains("Netty")) {
            return "network";
        } else if (threadName.contains("Worker")) {
            return "worker";
        } else if (threadName.contains("Chunk")) {
            return "chunk";
        } else if (threadName.contains("IO")) {
            return "io";
        } else {
            return "other";
        }
    }

    /**
     * Collects garbage collection metrics
     */
    private void collectGcMetrics(MetricsManager manager, long timestamp) {
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName().replace(" ", "_").toLowerCase();

            long collectionCount = gcBean.getCollectionCount();
            long collectionTime = gcBean.getCollectionTime();

            // Record total values
            manager.recordMetric("gc." + gcName + ".count", timestamp, collectionCount);
            manager.recordMetric("gc." + gcName + ".time", timestamp, collectionTime);

            // Calculate changes since last collection
            String gcKey = gcBean.getName();
            if (lastGcCounts.containsKey(gcKey)) {
                long countDiff = collectionCount - lastGcCounts.get(gcKey);
                long timeDiff = collectionTime - lastGcTimes.get(gcKey);

                // Record rate metrics
                manager.recordMetric("gc." + gcName + ".rate", timestamp, countDiff);
                if (countDiff > 0) {
                    manager.recordMetric("gc." + gcName + ".avg_time", timestamp, timeDiff / countDiff);
                }
            }

            // Update last values
            lastGcCounts.put(gcKey, collectionCount);
            lastGcTimes.put(gcKey, collectionTime);
        }
    }

    /**
     * Collects server tick metrics and analyzes performance
     */
    private void collectTickMetrics(MetricsManager manager, MinecraftServer server, long timestamp) {
        // Get current tick time
        double currentTickTime = server.getAverageTickTime();

        // Record raw tick time
        manager.recordMetric("server.tick_time", timestamp, currentTickTime);

        // Calculate TPS from tick time
        double tps = Math.min(20.0, 1000.0 / Math.max(currentTickTime, 50.0));
        manager.recordMetric("server.tps", timestamp, tps);

        // Add to recent tick times (for statistical analysis)
        recentTickTimes.add(currentTickTime);
        while (recentTickTimes.size() > TICK_TIMES_WINDOW) {
            recentTickTimes.remove(0);
        }

        // Statistical analysis of tick times
        if (recentTickTimes.size() >= 20) { // Need at least 1 second worth of ticks
            // Calculate standard deviation to identify variance
            double mean = recentTickTimes.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = recentTickTimes.stream()
                    .mapToDouble(t -> Math.pow(t - mean, 2))
                    .average()
                    .orElse(0);
            double stdDev = Math.sqrt(variance);

            manager.recordMetric("server.tick_time.mean", timestamp, mean);
            manager.recordMetric("server.tick_time.std_dev", timestamp, stdDev);

            // Calculate percentiles for more detailed performance picture
            List<Double> sortedTimes = new ArrayList<>(recentTickTimes);
            Collections.sort(sortedTimes);

            int size = sortedTimes.size();
            double median = size % 2 == 0
                    ? (sortedTimes.get(size / 2 - 1) + sortedTimes.get(size / 2)) / 2
                    : sortedTimes.get(size / 2);

            int p95Index = (int) Math.ceil(size * 0.95) - 1;
            int p99Index = (int) Math.ceil(size * 0.99) - 1;

            double p95 = sortedTimes.get(p95Index);
            double p99 = sortedTimes.get(p99Index);

            manager.recordMetric("server.tick_time.median", timestamp, median);
            manager.recordMetric("server.tick_time.p95", timestamp, p95);
            manager.recordMetric("server.tick_time.p99", timestamp, p99);

            // Lag spike detection
            if (currentTickTime > LAG_SPIKE_THRESHOLD_MS) {
                // Record this as a lag spike
                LagSpike spike = new LagSpike(timestamp, currentTickTime);
                recentLagSpikes.add(spike);

                // Keep only recent spikes
                while (recentLagSpikes.size() > MAX_LAG_SPIKES) {
                    recentLagSpikes.poll();
                }

                // Record spike metrics
                int spikesLast10Sec = countSpikesInWindow(10000, timestamp);
                int spikesLast60Sec = countSpikesInWindow(60000, timestamp);

                manager.recordMetric("server.lag_spikes.current", timestamp, currentTickTime);
                manager.recordMetric("server.lag_spikes.count_10s", timestamp, spikesLast10Sec);
                manager.recordMetric("server.lag_spikes.count_60s", timestamp, spikesLast60Sec);
            }
        }
    }

    /**
     * Counts lag spikes within a time window
     * @param windowMs Window size in milliseconds
     * @param currentTime Current timestamp
     * @return Number of spikes within the window
     */
    private int countSpikesInWindow(long windowMs, long currentTime) {
        return (int) recentLagSpikes.stream()
                .filter(spike -> spike.timestamp > currentTime - windowMs)
                .count();
    }

    /**
     * Gets the recent lag spikes for the web UI
     * @return List of recent lag spikes
     */
    public List<LagSpike> getRecentLagSpikes() {
        return new ArrayList<>(recentLagSpikes);
    }

    /**
     * Simple class to represent a performance lag spike
     */
    public static class LagSpike {
        public final long timestamp;
        public final double tickTimeMs;
        public double mspt;

        public LagSpike(long timestamp, double tickTimeMs) {
            this.timestamp = timestamp;
            this.tickTimeMs = tickTimeMs;
            this.mspt = mspt;
        }
    }
}