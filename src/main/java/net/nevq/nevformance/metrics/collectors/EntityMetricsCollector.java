package net.nevq.nevformance.metrics.collectors;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.nevq.nevformance.metrics.MetricsManager;
import net.nevq.nevformance.util.MetricsUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

/**
 * Specialized collector for entity-related metrics
 */
public class EntityMetricsCollector implements MetricCollector {

    // Cache for entity counts by type
    private final Map<EntityType<?>, Integer> lastEntityCountsByType = new ConcurrentHashMap<>();

    // Cache for entity distribution by chunk
    private final Map<ChunkPos, Map<String, Integer>> entityDensityMap = new ConcurrentHashMap<>();

    // Thresholds for entity concentration warnings
    private static final int ENTITY_CONCENTRATION_WARNING = 20; // Entities per chunk
    private static final int ENTITY_TYPE_CONCENTRATION_WARNING = 12; // Same type entities per chunk

    @Override
    public void collect(MetricsManager manager, MinecraftServer server, long timestamp) {
        // Reset caches for this collection cycle
        lastEntityCountsByType.clear();
        entityDensityMap.clear();

        // Track totals across all dimensions
        int totalEntities = 0;
        int totalLivingEntities = 0;
        int totalMobs = 0;
        int totalPassive = 0;

        // Entity breakdown by major categories
        Map<String, Integer> entityCategories = new HashMap<>();
        entityCategories.put("hostile", 0);
        entityCategories.put("passive", 0);
        entityCategories.put("player", 0);
        entityCategories.put("projectile", 0);
        entityCategories.put("item", 0);
        entityCategories.put("other", 0);

        // Process each world/dimension
        for (ServerWorld world : server.getWorlds()) {
            String dimensionKey = MetricsUtil.getDimensionKey(world);
            String worldPrefix = "world." + dimensionKey;

            // Category counts for this dimension
            Map<String, Integer> dimensionCategories = new HashMap<>(entityCategories);

            // Use a map to count entities by type for this dimension
            Map<EntityType<?>, Integer> entityCountsByType = new HashMap<>();
            Map<ChunkPos, Map<EntityType<?>, Integer>> entitiesByChunk = new HashMap<>();

            // Count entities in this dimension
            int worldEntityCount = 0;

            // Process each entity
            for (Entity entity : world.iterateEntities()) {
                worldEntityCount++;
                totalEntities++;

                // Increment count for this entity type
                entityCountsByType.merge(entity.getType(), 1, Integer::sum);
                lastEntityCountsByType.merge(entity.getType(), 1, Integer::sum);

                // Track by chunk position for density analysis
                ChunkPos chunkPos = new ChunkPos(entity.getBlockPos());
                entitiesByChunk
                        .computeIfAbsent(chunkPos, k -> new HashMap<>())
                        .merge(entity.getType(), 1, Integer::sum);

                // Categorize entity
                String category = categorizeEntity(entity);
                dimensionCategories.merge(category, 1, Integer::sum);
                entityCategories.merge(category, 1, Integer::sum);

                // Count entity by broader type
                if (entity instanceof LivingEntity) {
                    totalLivingEntities++;
                    if (entity instanceof MobEntity) {
                        totalMobs++;
                    } else if (entity instanceof PassiveEntity) {
                        totalPassive++;
                    }
                }
            }

            // Record general counts for this dimension
            manager.recordMetric(worldPrefix + ".entities.total", timestamp, worldEntityCount);

            // Record category breakdown for this dimension
            for (Map.Entry<String, Integer> entry : dimensionCategories.entrySet()) {
                manager.recordMetric(
                        worldPrefix + ".entities." + entry.getKey(),
                        timestamp,
                        entry.getValue()
                );
            }

            // Record top entity types for this dimension
            entityCountsByType.entrySet().stream()
                    .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                    .limit(10) // Top 10 entity types
                    .forEach(entry -> {
                        String entityTypeName = EntityType.getId(entry.getKey()).toString().replace(":", ".");
                        manager.recordMetric(
                                worldPrefix + ".entities.types." + entityTypeName,
                                timestamp,
                                entry.getValue()
                        );
                    });

            // Identify entity hotspots
            entitiesByChunk.entrySet().stream()
                    .filter(entry -> {
                        // Get total entities in this chunk
                        int totalInChunk = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();
                        return totalInChunk >= ENTITY_CONCENTRATION_WARNING;
                    })
                    .forEach(entry -> {
                        ChunkPos pos = entry.getKey();
                        int totalInChunk = entry.getValue().values().stream().mapToInt(Integer::intValue).sum();

                        // Store for web UI to access
                        Map<String, Integer> chunkData = new HashMap<>();
                        chunkData.put("total", totalInChunk);
                        entry.getValue().forEach((type, count) -> {
                            String typeName = EntityType.getId(type).toString().replace(":", ".");
                            chunkData.put(typeName, count);

                            // Record individual high concentrations of specific types
                            if (count >= ENTITY_TYPE_CONCENTRATION_WARNING) {
                                manager.recordMetric(
                                        String.format("%s.hotspot.%d.%d.%s", worldPrefix, pos.x, pos.z, typeName),
                                        timestamp,
                                        count
                                );
                            }
                        });

                        entityDensityMap.put(pos, chunkData);

                        // Record the total for this hotspot
                        manager.recordMetric(
                                String.format("%s.hotspot.%d.%d.total", worldPrefix, pos.x, pos.z),
                                timestamp,
                                totalInChunk
                        );
                    });
        }

        // Record global metrics
        manager.recordMetric("entities.total", timestamp, totalEntities);
        manager.recordMetric("entities.living", timestamp, totalLivingEntities);
        manager.recordMetric("entities.hostile", timestamp, entityCategories.get("hostile"));
        manager.recordMetric("entities.passive", timestamp, entityCategories.get("passive"));

        // Record top entity types globally
        lastEntityCountsByType.entrySet().stream()
                .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                .limit(15) // Top 15 entity types globally
                .forEach(entry -> {
                    String entityTypeName = EntityType.getId(entry.getKey()).toString().replace(":", ".");
                    manager.recordMetric(
                            "entities.types." + entityTypeName,
                            timestamp,
                            entry.getValue()
                    );
                });
    }

    /**
     * Categorizes an entity into a general category
     * @param entity The entity to categorize
     * @return The category name
     */
    private String categorizeEntity(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return "player";
        } else if (entity instanceof MobEntity) {
            return "hostile";
        } else if (entity instanceof PassiveEntity) {
            return "passive";
        } else if (entity.getType() == EntityType.ARROW ||
                entity.getType() == EntityType.SPECTRAL_ARROW ||
                entity.getType() == EntityType.TRIDENT) {
            return "projectile";
        } else if (entity.getType() == EntityType.ITEM) {
            return "item";
        } else {
            return "other";
        }
    }

    /**
     * Gets the entity density map for rendering in the web UI
     * @return Map of chunk positions to entity counts by type
     */
    public Map<ChunkPos, Map<String, Integer>> getEntityDensityMap() {
        return entityDensityMap;
    }
}