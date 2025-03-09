package net.nevq.nevformance.metrics;

import net.nevq.nevformance.Nevformance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("nevformance");

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<MetricCollector> collectors = new ArrayList<>();
    private final Map<String, CircularMetricBuffer> metricBuffers = new HashMap<>();

    private MinecraftServer server;
    private boolean isCollecting = false;

    // System resources for monitoring
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

    public MetricsManager() {
        // Initialize default metric collectors
        collectors.add(new ServerMetricsCollector());
        collectors.add(new WorldMetricsCollector());

        // Initialize metric buffers
        initializeMetricBuffers();
    }

    private void initializeMetricBuffers() {
        int historySize = Nevformance.getInstance().getConfigManager().getMetricsHistorySize();

        // Server-wide metrics
        metricBuffers.put("tps", new CircularMetricBuffer(historySize));
        metricBuffers.put("mspt", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.used", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.max", new CircularMetricBuffer(historySize));
        metricBuffers.put("cpu.system", new CircularMetricBuffer(historySize));
        metricBuffers.put("cpu.process", new CircularMetricBuffer(historySize));
        metricBuffers.put("entities.total", new CircularMetricBuffer(historySize));
        metricBuffers.put("chunks.loaded", new CircularMetricBuffer(historySize));

        // We'll add more specific metrics as needed
    }

    public void startCollection(MinecraftServer server) {
        if (isCollecting) {
            return;
        }

        this.server = server;
        isCollecting = true;

        // Schedule metric collection based on the configured interval
        int interval = Nevformance.getInstance().getConfigManager().getMetricCollectionIntervalMs();
        LOGGER.info("Starting metric collection with interval of {} ms", interval);

        scheduler.scheduleAtFixedRate(this::collectMetrics, 0, interval, TimeUnit.MILLISECONDS);
    }

    public void stopCollection() {
        isCollecting = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("Metric collection stopped");
    }

    private void collectMetrics() {
        if (server == null || !isCollecting) {
            return;
        }

        try {
            long timestamp = System.currentTimeMillis();

            // Collect global metrics
            collectGlobalMetrics(timestamp);

            // Let each collector gather its metrics
            for (MetricCollector collector : collectors) {
                CompletableFuture.runAsync(() -> {
                    try {
                        collector.collect(this, server, timestamp);
                    } catch (Exception e) {
                        LOGGER.error("Error in metric collector {}", collector.getClass().getSimpleName(), e);
                    }
                });
            }
        } catch (Exception e) {
            LOGGER.error("Error collecting metrics", e);
        }
    }

    private void collectGlobalMetrics(long timestamp) {
        // TPS calculation
        double tps = calculateTPS();
        recordMetric("tps", timestamp, tps);

        // MSPT (Milliseconds per tick)
        double mspt = calculateMSPT();
        recordMetric("mspt", timestamp, mspt);

        // Memory usage
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024); // MB
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024); // MB
        recordMetric("memory.used", timestamp, usedMemory);
        recordMetric("memory.max", timestamp, maxMemory);

        // CPU usage (if available)
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = (com.sun.management.OperatingSystemMXBean) osBean;
            double systemCpuLoad = sunOsBean.getCpuLoad() * 100.0; // Convert to percentage
            double processCpuLoad = sunOsBean.getProcessCpuLoad() * 100.0; // Convert to percentage

            recordMetric("cpu.system", timestamp, systemCpuLoad);
            recordMetric("cpu.process", timestamp, processCpuLoad);
        }

        // Entity count - using a version-compatible approach
        int totalEntities = 0;
        for (ServerWorld world : server.getWorlds()) {
            // Count entities by iterating through entity lists
            // This is less efficient but more compatible across versions
            final AtomicInteger totalEntitiesAtomic = new AtomicInteger(totalEntities);
            world.iterateEntities().forEach(entity -> totalEntitiesAtomic.incrementAndGet());
            totalEntities = totalEntitiesAtomic.get();
        }
        recordMetric("entities.total", timestamp, totalEntities);

        // Loaded chunks
        int loadedChunks = 0;
        for (ServerWorld world : server.getWorlds()) {
            loadedChunks += world.getChunkManager().getLoadedChunkCount();
        }
        recordMetric("chunks.loaded", timestamp, loadedChunks);
    }

    private double calculateTPS() {
        // This is an approximation and may need adjustment based on how Minecraft reports tick rates
        double meanTickTime = server.getAverageTickTime();
        double tps = 1000.0 / Math.max(meanTickTime, 50.0); // Capped at 20 TPS
        return Math.min(20.0, tps);
    }

    private double calculateMSPT() {
        return server.getAverageTickTime();
    }

    public void recordMetric(String metricName, long timestamp, double value) {
        CircularMetricBuffer buffer = metricBuffers.get(metricName);
        if (buffer == null) {
            // Create buffer if it doesn't exist yet
            int historySize = Nevformance.getInstance().getConfigManager().getMetricsHistorySize();
            buffer = new CircularMetricBuffer(historySize);
            metricBuffers.put(metricName, buffer);
        }

        buffer.add(new MetricPoint(timestamp, value));
    }

    public Map<String, List<MetricPoint>> getMetrics() {
        Map<String, List<MetricPoint>> result = new HashMap<>();

        for (Map.Entry<String, CircularMetricBuffer> entry : metricBuffers.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getPoints());
        }

        return result;
    }

    public List<MetricPoint> getMetric(String metricName) {
        CircularMetricBuffer buffer = metricBuffers.get(metricName);
        return buffer != null ? buffer.getPoints() : Collections.emptyList();
    }

    // Inner classes for metric collectors

    public interface MetricCollector {
        void collect(MetricsManager manager, MinecraftServer server, long timestamp);
    }

    private static class ServerMetricsCollector implements MetricCollector {
        @Override
        public void collect(MetricsManager manager, MinecraftServer server, long timestamp) {
            // Additional server-wide metrics can be collected here
            // These are more advanced metrics beyond the basics in collectGlobalMetrics

            // For example, network IO metrics, thread stats, etc.
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();

            manager.recordMetric("threads.current", timestamp, threadCount);
            manager.recordMetric("threads.peak", timestamp, peakThreadCount);
        }
    }

    private static class WorldMetricsCollector implements MetricCollector {
        @Override
        public void collect(MetricsManager manager, MinecraftServer server, long timestamp) {
            // Collect per-dimension metrics
            for (ServerWorld world : server.getWorlds()) {
                String dimensionKey = world.getRegistryKey().getValue().toString().replace(":", ".");
                String prefix = "world." + dimensionKey;

                // Entity counts per dimension - using a compatible approach
                final int[] entityCount = {0};
                world.iterateEntities().forEach(entity -> entityCount[0]++);
                manager.recordMetric(prefix + ".entities", timestamp, entityCount[0]);

                // Chunk counts
                int loadedChunks = world.getChunkManager().getLoadedChunkCount();
                manager.recordMetric(prefix + ".chunks", timestamp, loadedChunks);

                // More detailed metrics can be added here

                // If enabled, collect chunk-specific metrics
                if (Nevformance.getInstance().getConfigManager().isCollectChunkMetrics()) {
                    collectChunkMetrics(manager, world, timestamp);
                }
            }
        }

        private void collectChunkMetrics(MetricsManager manager, ServerWorld world, long timestamp) {
            // This can be computationally expensive, so we might want to sample
            // rather than collecting data for every chunk

            // Get active chunks (could be optimized/sampled in a production environment)
            String dimensionKey = world.getRegistryKey().getValue().toString().replace(":", ".");
            //Map<ChunkPos, Integer> entityCountsPerChunk = new HashMap<>();

            // Chunk analysis - using a more version-compatible approach
            Map<ChunkPos, Integer> entityCountsPerChunk = new HashMap<>();

            // Count entities per chunk - this is a simplification
            world.iterateEntities().forEach(entity -> {
                ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                entityCountsPerChunk.merge(chunkPos, 1, Integer::sum);
            });

            // Record hotspots (chunks with many entities)
            entityCountsPerChunk.entrySet().stream()
                    .filter(entry -> entry.getValue() > 20) // Threshold for "many entities"
                    .forEach(entry -> {
                        ChunkPos pos = entry.getKey();
                        String metricName = String.format(
                                "hotspot.%s.chunk.%d.%d.entities",
                                dimensionKey,
                                pos.x,
                                pos.z
                        );
                        manager.recordMetric(metricName, timestamp, entry.getValue());
                    });
        }
    }
}