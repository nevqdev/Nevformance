package net.nevq.nevformance.metrics.collectors;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.nevq.nevformance.metrics.MetricsManager;
import net.nevq.nevformance.util.MetricsUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specialized collector for world and chunk-related metrics
 */
public class WorldMetricsCollector implements MetricCollector {

    // Cache for block entity statistics
    private final Map<String, Integer> blockEntityCountsByType = new ConcurrentHashMap<>();

    // Cache for chunk activity scores
    private final Map<ChunkPos, Double> chunkActivityScores = new ConcurrentHashMap<>();

    // Threshold for considering a chunk "active"
    private static final double CHUNK_ACTIVITY_THRESHOLD = 0.7;

    // Window size for tracking chunk load/unload rates (milliseconds)
    private static final long RATE_WINDOW_SIZE = 60000;

    // Tracking chunk load/unload events
    private final Queue<Long> chunkLoadTimestamps = new ArrayDeque<>();
    private final Queue<Long> chunkUnloadTimestamps = new ArrayDeque<>();

    @Override
    public void collect(MetricsManager manager, MinecraftServer server, long timestamp) {
        // Reset caches for this collection cycle
        blockEntityCountsByType.clear();
        chunkActivityScores.clear();

        // Track totals across all dimensions
        int totalLoadedChunks = 0;
        int totalBlockEntities = 0;

        // Process each world/dimension
        for (ServerWorld world : server.getWorlds()) {
            String dimensionKey = MetricsUtil.getDimensionKey(world);
            String worldPrefix = "world." + dimensionKey;

            // Count loaded chunks in this dimension
            int loadedChunks = 0;
            int blockEntitiesCount = 0;

            // Track chunk status counts
            Map<ChunkStatus, Integer> chunkStatusCounts = new HashMap<>();

            // Track block entity types in this dimension
            Map<String, Integer> dimensionBlockEntityTypes = new HashMap<>();

            // Get chunk manager for this world
            ServerChunkManager chunkManager = world.getChunkManager();

            // Analyze loaded chunks
            Collection<WorldChunk> loadedChunkList = getLoadedChunks(world);

            // Count total loaded chunks
            loadedChunks = loadedChunkList.size();
            totalLoadedChunks += loadedChunks;

            // Analyze each chunk
            for (WorldChunk chunk : loadedChunkList) {
                // Analyze chunk activity and score it
                double activityScore = calculateChunkActivityScore(chunk);
                ChunkPos pos = chunk.getPos();
                chunkActivityScores.put(pos, activityScore);

                // If chunk is highly active, record it
                if (activityScore > CHUNK_ACTIVITY_THRESHOLD) {
                    manager.recordMetric(
                            String.format("%s.active_chunk.%d.%d", worldPrefix, pos.x, pos.z),
                            timestamp,
                            activityScore
                    );
                }

                // Count block entities in this chunk and get map of types
                Map<String, Integer> blockEntityTypesInChunk = countBlockEntities(chunk);

                // Use a local counter to avoid modifying variables in lambda
                final int[] entityCount = {0};

                // Process each block entity type
                blockEntityTypesInChunk.forEach((type, count) -> {
                    dimensionBlockEntityTypes.merge(type, count, Integer::sum);
                    blockEntityCountsByType.merge(type, count, Integer::sum);
                    entityCount[0] += count;
                });

                // Update the counters after lambda execution
                blockEntitiesCount += entityCount[0];
                totalBlockEntities += entityCount[0];

                // Count chunk by status
                chunkStatusCounts.merge(chunk.getStatus(), 1, Integer::sum);
            }

            // Record general counts for this dimension
            manager.recordMetric(worldPrefix + ".chunks.loaded", timestamp, loadedChunks);
            manager.recordMetric(worldPrefix + ".block_entities.total", timestamp, blockEntitiesCount);

            // Record chunk status breakdown
            final String finalWorldPrefix = worldPrefix;
            final long finalTimestamp = timestamp;
            chunkStatusCounts.forEach((status, count) -> {
                manager.recordMetric(
                        finalWorldPrefix + ".chunks.status." + status.getId(),
                        finalTimestamp,
                        count
                );
            });

            // Record top block entity types for this dimension
            final String metricWorldPrefix = worldPrefix;
            final long metricTimestamp = timestamp;
            dimensionBlockEntityTypes.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10) // Top 10 block entity types
                    .forEach(entry -> {
                        manager.recordMetric(
                                metricWorldPrefix + ".block_entities.types." + entry.getKey().replace(":", "."),
                                metricTimestamp,
                                entry.getValue()
                        );
                    });
        }

        // Calculate and record chunk load/unload rates
        updateChunkLoadRates(timestamp);
        double chunkLoadRate = calculateChunkRate(chunkLoadTimestamps, timestamp);
        double chunkUnloadRate = calculateChunkRate(chunkUnloadTimestamps, timestamp);

        manager.recordMetric("chunks.load_rate", timestamp, chunkLoadRate);
        manager.recordMetric("chunks.unload_rate", timestamp, chunkUnloadRate);

        // Record global metrics
        manager.recordMetric("chunks.loaded", timestamp, totalLoadedChunks);
        manager.recordMetric("block_entities.total", timestamp, totalBlockEntities);

        // Record top block entity types globally
        final long typeTimestamp = timestamp;
        blockEntityCountsByType.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(15) // Top 15 block entity types globally
                .forEach(entry -> {
                    manager.recordMetric(
                            "block_entities.types." + entry.getKey().replace(":", "."),
                            typeTimestamp,
                            entry.getValue()
                    );
                });
    }

    /**
     * Gets loaded chunks from the world
     * @param world The server world
     * @return Collection of loaded chunks
     */
    private Collection<WorldChunk> getLoadedChunks(ServerWorld world) {
        // This is a version-compatible approach to get loaded chunks
        List<WorldChunk> chunks = new ArrayList<>();

        // Scan a reasonable area around spawn for loaded chunks
        int scanRadius = 32; // Adjust this based on your server's view distance
        BlockPos spawnPos = world.getSpawnPos();
        int centerX = spawnPos.getX() >> 4; // Convert to chunk coordinates
        int centerZ = spawnPos.getZ() >> 4;

        for (int x = centerX - scanRadius; x <= centerX + scanRadius; x++) {
            for (int z = centerZ - scanRadius; z <= centerZ + scanRadius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    WorldChunk chunk = world.getChunk(x, z);
                    if (chunk != null) {
                        chunks.add(chunk);
                    }
                }
            }
        }

        return chunks;
    }

    /**
     * Calculates an activity score for a chunk based on various factors
     * @param chunk The chunk to analyze
     * @return Activity score between 0 and 1
     */
    private double calculateChunkActivityScore(WorldChunk chunk) {
        double score = 0.0;

        // Factor 1: Block entities (redstone, furnaces, etc.)
        int blockEntityCount = 0;
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            blockEntityCount++;

            // Additional score for "active" block entities like furnaces
            String blockEntityType = blockEntity.getType().toString();
            if (blockEntityType.contains("furnace") ||
                    blockEntityType.contains("hopper") ||
                    blockEntityType.contains("piston") ||
                    blockEntityType.contains("dispenser") ||
                    blockEntityType.contains("dropper")) {
                score += 0.1;
            }
        }

        // Base score from block entity count (max contribution: 0.3)
        score += Math.min(0.3, blockEntityCount * 0.03);

        // TODO: Add other factors like recent block updates, redstone activity
        // These require mixins to track during chunk updates

        return Math.min(1.0, score);
    }

    /**
     * Counts block entities in a chunk by type
     * @param chunk The chunk to analyze
     * @return Map of block entity types to counts
     */
    private Map<String, Integer> countBlockEntities(WorldChunk chunk) {
        Map<String, Integer> countsByType = new HashMap<>();

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            String type = blockEntity.getType().toString();
            countsByType.merge(type, 1, Integer::sum);
        }

        return countsByType;
    }

    /**
     * Updates the chunk load/unload rate trackers
     * @param currentTime Current timestamp
     */
    private void updateChunkLoadRates(long currentTime) {
        // Remove timestamps outside the window
        while (!chunkLoadTimestamps.isEmpty() && chunkLoadTimestamps.peek() < currentTime - RATE_WINDOW_SIZE) {
            chunkLoadTimestamps.poll();
        }

        while (!chunkUnloadTimestamps.isEmpty() && chunkUnloadTimestamps.peek() < currentTime - RATE_WINDOW_SIZE) {
            chunkUnloadTimestamps.poll();
        }
    }

    /**
     * Calculates the rate of events per minute based on timestamps in a window
     * @param timestamps Queue of event timestamps
     * @param currentTime Current timestamp
     * @return Rate of events per minute
     */
    private double calculateChunkRate(Queue<Long> timestamps, long currentTime) {
        // Calculate events per minute
        double windowSizeMinutes = RATE_WINDOW_SIZE / 60000.0;
        return timestamps.size() / windowSizeMinutes;
    }

    /**
     * Registers a chunk load event
     * @param timestamp Time when chunk was loaded
     */
    public void registerChunkLoad(long timestamp) {
        chunkLoadTimestamps.add(timestamp);
    }

    /**
     * Registers a chunk unload event
     * @param timestamp Time when chunk was unloaded
     */
    public void registerChunkUnload(long timestamp) {
        chunkUnloadTimestamps.add(timestamp);
    }

    /**
     * Gets the activity scores for chunks
     * @return Map of chunk positions to activity scores
     */
    public Map<ChunkPos, Double> getChunkActivityScores() {
        return chunkActivityScores;
    }
}