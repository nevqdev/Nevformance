package net.nevq.nevformance.metrics;

import net.nevq.nevformance.Nevformance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import net.nevq.nevformance.metrics.collectors.*;

public class MetricsManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("nevformance");

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final List<MetricCollector> collectors = new ArrayList<>();
    private final Map<String, CircularMetricBuffer> metricBuffers = new HashMap<>();

    private MinecraftServer server;
    private boolean isCollecting = false;

    // Specialized collectors
    private final EntityMetricsCollector entityCollector;
    private final WorldMetricsCollector worldCollector;
    private final SystemMetricsCollector systemCollector;

    public MetricsManager() {
        // Initialize specialized metric collectors
        entityCollector = new EntityMetricsCollector();
        worldCollector = new WorldMetricsCollector();
        systemCollector = new SystemMetricsCollector();

        // Add all collectors to the main list
        collectors.add(entityCollector);
        collectors.add(worldCollector);
        collectors.add(systemCollector);

        // Initialize metric buffers
        initializeMetricBuffers();
    }

    private void initializeMetricBuffers() {
        int historySize = Nevformance.getInstance().getConfigManager().getMetricsHistorySize();

        // Server-wide metrics
        metricBuffers.put("server.tps", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time.mean", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time.std_dev", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time.median", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time.p95", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.tick_time.p99", new CircularMetricBuffer(historySize));

        // Memory metrics
        metricBuffers.put("memory.heap.used", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.heap.committed", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.heap.max", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.nonheap.used", new CircularMetricBuffer(historySize));
        metricBuffers.put("memory.heap.utilization", new CircularMetricBuffer(historySize));

        // CPU metrics
        metricBuffers.put("cpu.system", new CircularMetricBuffer(historySize));
        metricBuffers.put("cpu.process", new CircularMetricBuffer(historySize));

        // Entity metrics
        metricBuffers.put("entities.total", new CircularMetricBuffer(historySize));
        metricBuffers.put("entities.living", new CircularMetricBuffer(historySize));
        metricBuffers.put("entities.hostile", new CircularMetricBuffer(historySize));
        metricBuffers.put("entities.passive", new CircularMetricBuffer(historySize));

        // Chunk metrics
        metricBuffers.put("chunks.loaded", new CircularMetricBuffer(historySize));
        metricBuffers.put("chunks.load_rate", new CircularMetricBuffer(historySize));
        metricBuffers.put("chunks.unload_rate", new CircularMetricBuffer(historySize));

        // GC metrics
        metricBuffers.put("gc.young.rate", new CircularMetricBuffer(historySize));
        metricBuffers.put("gc.old.rate", new CircularMetricBuffer(historySize));

        // Thread metrics
        metricBuffers.put("threads.active", new CircularMetricBuffer(historySize));

        // Lag spike metrics
        metricBuffers.put("server.lag_spikes.current", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.lag_spikes.count_10s", new CircularMetricBuffer(historySize));
        metricBuffers.put("server.lag_spikes.count_60s", new CircularMetricBuffer(historySize));
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

            // Remove call to GlobalMetrics
            // Collect global metrics
            // collectGlobalMetrics(timestamp);

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

    public Map<String, List<MetricPoint>> getMetricsByPrefix(String prefix) {
        Map<String, List<MetricPoint>> result = new HashMap<>();

        for (Map.Entry<String, CircularMetricBuffer> entry : metricBuffers.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.put(entry.getKey(), entry.getValue().getPoints());
            }
        }

        return result;
    }

    /**
     * Gets a set of all available metric names
     * @return Set of metric names
     */
    public Set<String> getAvailableMetrics() {
        return new HashSet<>(metricBuffers.keySet());
    }

    /**
     * Gets the entity metrics collector
     * @return EntityMetricsCollector instance
     */
    public EntityMetricsCollector getEntityCollector() {
        return entityCollector;
    }

    /**
     * Gets the world metrics collector
     * @return WorldMetricsCollector instance
     */
    public WorldMetricsCollector getWorldCollector() {
        return worldCollector;
    }

    /**
     * Gets the system metrics collector
     * @return SystemMetricsCollector instance
     */
    public SystemMetricsCollector getSystemCollector() {
        return systemCollector;
    }
}