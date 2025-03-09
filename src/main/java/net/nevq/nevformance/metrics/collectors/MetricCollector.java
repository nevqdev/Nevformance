package net.nevq.nevformance.metrics.collectors;

import net.minecraft.server.MinecraftServer;
import net.nevq.nevformance.metrics.MetricsManager;

/**
 * Interface for all metric collectors
 * Collectors are specialized components that gather specific types of performance data
 */
public interface MetricCollector {
    /**
     * Collects metrics and records them in the metrics manager
     *
     * @param manager The metrics manager to record metrics to
     * @param server The Minecraft server instance
     * @param timestamp The current timestamp in milliseconds
     */
    void collect(MetricsManager manager, MinecraftServer server, long timestamp);
}