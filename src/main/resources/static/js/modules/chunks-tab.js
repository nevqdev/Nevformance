// Chunks tab JavaScript - Handles chunk-related charts and metrics

// Import utility functions
import { formatTime, formatDimensionName, formatBlockEntityType } from '../utils/chart-utils.js';
import { filterMetricsByTimeRange } from '../main.js';

// Global chart variables
let chunksByDimensionChart = null;
let chunkRateChart = null;
let blockEntitiesChart = null;

/**
 * Initializes all chunk tab charts
 */
export function initChunkCharts() {
    // Chunks by Dimension chart
    const chunkDimCtx = document.getElementById('chunks-by-dimension-chart').getContext('2d');
    chunksByDimensionChart = new Chart(chunkDimCtx, {
        type: 'pie',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: [
                    'rgba(54, 162, 235, 0.7)',
                    'rgba(255, 99, 132, 0.7)',
                    'rgba(255, 206, 86, 0.7)',
                    'rgba(75, 192, 192, 0.7)',
                    'rgba(153, 102, 255, 0.7)'
                ],
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: {
                legend: {
                    position: 'right'
                },
                title: {
                    display: true,
                    text: 'Chunks by Dimension'
                }
            }
        }
    });

    // Chunk Load/Unload Rate chart
    const chunkRateCtx = document.getElementById('chunk-rate-chart').getContext('2d');
    chunkRateChart = new Chart(chunkRateCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Load Rate',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
                    data: [],
                    tension: 0.2
                },
                {
                    label: 'Unload Rate',
                    borderColor: 'rgba(255, 99, 132, 1)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    data: [],
                    tension: 0.2
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Chunks per minute'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'Time'
                    }
                }
            }
        }
    });

    // Block Entities chart
    const blockEntitiesCtx = document.getElementById('block-entities-chart').getContext('2d');
    blockEntitiesChart = new Chart(blockEntitiesCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Count',
                data: [],
                backgroundColor: 'rgba(153, 102, 255, 0.7)',
                borderColor: 'rgba(153, 102, 255, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            plugins: {
                title: {
                    display: true,
                    text: 'Top Block Entity Types'
                }
            },
            scales: {
                x: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Count'
                    }
                }
            }
        }
    });
}

/**
 * Updates all chunk-related charts with the latest data
 * @param {Object} appState Application state containing metrics data and configuration
 */
export function updateChunkCharts(appState) {
    // Fetch chunk metrics
    Promise.all([
        fetch('/api/metrics?prefix=world').then(response => response.json()),
        fetch('/api/metrics?prefix=chunks').then(response => response.json()),
        fetch('/api/metrics?prefix=block_entities').then(response => response.json())
    ])
        .then(([worldData, chunkData, blockEntityData]) => {
            updateChunksByDimensionChart(worldData);
            updateChunkRateChart(chunkData, appState.selectedTimeRange);
            updateBlockEntitiesChart(blockEntityData);
            updateActiveChunksTable(worldData);
        })
        .catch(error => {
            console.error('Error fetching chunk metrics:', error);
        });
}

/**
 * Updates the chunks by dimension pie chart
 */
function updateChunksByDimensionChart(worldData) {
    const dimensions = {};

    // Extract chunk counts by dimension
    Object.entries(worldData).forEach(([key, points]) => {
        if (!key.includes('.chunks.loaded') || points.length === 0) return;

        // Extract dimension name from the key (e.g., world.minecraft.overworld.chunks.loaded)
        const parts = key.split('.');
        if (parts.length >= 3) {
            const dimension = `${parts[1]}.${parts[2]}`;
            const count = points[points.length - 1].value;

            if (count > 0) {
                dimensions[dimension] = count;
            }
        }
    });

    // Update chart
    chunksByDimensionChart.data.labels = Object.keys(dimensions).map(formatDimensionName);
    chunksByDimensionChart.data.datasets[0].data = Object.values(dimensions);
    chunksByDimensionChart.update();
}

/**
 * Updates the chunk load/unload rate chart
 * @param {Object} chunkData Chunk metrics data
 * @param {string} timeRange Selected time range
 */
function updateChunkRateChart(chunkData, timeRange) {
    if (!chunkData['chunks.load_rate'] || !chunkData['chunks.unload_rate']) return;

    const loadRateData = filterMetricsByTimeRange(chunkData['chunks.load_rate']);
    const unloadRateData = filterMetricsByTimeRange(chunkData['chunks.unload_rate']);

    // Get timestamps from load rate data
    const timestamps = loadRateData.map(point => formatTime(point.timestamp));

    // Update chart
    chunkRateChart.data.labels = timestamps;
    chunkRateChart.data.datasets[0].data = loadRateData.map(point => point.value);
    chunkRateChart.data.datasets[1].data = unloadRateData.map(point => point.value);
    chunkRateChart.update();
}

/**
 * Updates the block entities bar chart
 */
function updateBlockEntitiesChart(blockEntityData) {
    const blockEntities = {};

    console.log('Block entity data keys:', Object.keys(blockEntityData));

    // Extract block entity types
    Object.entries(blockEntityData).forEach(([key, points]) => {
        if (!key.startsWith('block_entities.types.') || points.length === 0) return;

        // Extract the entity type name from the key
        const blockEntityType = key.replace('block_entities.types.', '');

        // Log raw type for debugging
        console.log('Block entity raw type:', blockEntityType);

        // Get the latest value
        const count = points[points.length - 1].value;

        if (count > 0) {
            blockEntities[blockEntityType] = count;
        }
    });

    if (Object.keys(blockEntities).length === 0) {
        // No block entity data available
        blockEntitiesChart.data.labels = ['No block entity data'];
        blockEntitiesChart.data.datasets[0].data = [0];
        blockEntitiesChart.update();
        return;
    }

    // Sort by count and take top 10
    const sortedTypes = Object.entries(blockEntities)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);

    console.log('Sorted block entity types:', sortedTypes);

    // Format the entity types for display
    const formattedTypes = sortedTypes.map(([type, _]) => {
        console.log("Raw entity type:", type);

        // For net.minecraft.block.entity.XxxBlockEntity pattern
        if (type.includes('net.minecraft.block.entity.')) {
            let entityName = type.split('.').pop();

            // Remove "BlockEntity" suffix if present
            entityName = entityName.replace(/BlockEntity$/, '');

            // Format nicely (camelCase to Title Case With Spaces)
            entityName = entityName
                // Add space before capitals, e.g., "SignBlockEntity" -> "Sign Block Entity"
                .replace(/([A-Z])/g, ' $1')
                // Trim leading space and capitalize first letter
                .trim();

            return entityName;
        }

        // For other patterns, keep existing logic
        if (type.includes('@')) {
            const typeParts = type.split('@');
            let readableName = typeParts[0].replace('BlockEntityType', '');
            if (readableName === '') {
                readableName = 'Entity-' + typeParts[1].substring(0, 4);
            }
            return readableName;
        }

        if (type.includes(':')) {
            return formatBlockEntityType(type);
        }

        if (type.includes('.')) {
            const lastPart = type.split('.').pop();
            return formatBlockEntityType(lastPart);
        }

        return formatBlockEntityType(type);
    });

    // Update chart
    blockEntitiesChart.data.labels = formattedTypes;
    blockEntitiesChart.data.datasets[0].data = sortedTypes.map(([_, count]) => count);
    blockEntitiesChart.update();
}

/**
 * Updates the active chunks table
 */
function updateActiveChunksTable(worldData) {
    const table = document.getElementById('active-chunks-table').querySelector('tbody');
    table.innerHTML = '';

    console.log('World data keys:', Object.keys(worldData));

    // Check if we have any active chunk data
    const hasActiveChunkData = Object.keys(worldData).some(key => key.includes('.active_chunk.'));

    if (!hasActiveChunkData) {
        // No active chunk data available yet
        const row = document.createElement('tr');
        row.innerHTML = `<td colspan="4">No active chunk data available. This might be populated as more data is collected.</td>`;
        table.appendChild(row);
        return;
    }

    const activeChunks = [];

    // Extract active chunks from metrics
    Object.entries(worldData).forEach(([key, points]) => {
        if (!key.includes('.active_chunk.') || points.length === 0) return;

        console.log('Processing active chunk key:', key);

        // Parse key format: world.dimension.active_chunk.x.z
        const parts = key.split('.');
        if (parts.length >= 5) {
            const dimension = `${parts[1]}.${parts[2]}`;
            const x = parseInt(parts[4]);
            const z = parseInt(parts[5]);
            const activityScore = points[points.length - 1].value;

            // Find block entity count for this chunk
            let blockEntityCount = 0;
            const blockEntityKey = `world.${dimension}.chunk.${x}.${z}.block_entities`;
            if (worldData[blockEntityKey] && worldData[blockEntityKey].length > 0) {
                blockEntityCount = worldData[blockEntityKey][worldData[blockEntityKey].length - 1].value;
            }

            activeChunks.push({
                dimension,
                x,
                z,
                activityScore,
                blockEntityCount
            });
        }
    });

    if (activeChunks.length === 0) {
        // No active chunks after processing
        const row = document.createElement('tr');
        row.innerHTML = `<td colspan="4">No active chunks detected. This might be populated as more chunk activity data is collected.</td>`;
        table.appendChild(row);
        return;
    }

    // Sort by activity score (descending) and take top 15
    activeChunks.sort((a, b) => b.activityScore - a.activityScore)
        .slice(0, 15)
        .forEach(chunk => {
            const row = document.createElement('tr');

            row.innerHTML = `
                <td>${formatDimensionName(chunk.dimension)}</td>
                <td>${chunk.x}, ${chunk.z}</td>
                <td>${chunk.activityScore.toFixed(2)}</td>
                <td>${chunk.blockEntityCount}</td>
            `;

            table.appendChild(row);
        });
}