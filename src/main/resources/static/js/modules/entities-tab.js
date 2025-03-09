// Entities tab JavaScript - Handles entity-related charts and metrics
import { formatEntityType } from '../utils/chart-utils.js';

// Global chart variables
let entityDistributionChart = null;
let entityTypesChart = null;

/**
 * Initializes all entity tab charts
 */
export function initEntityCharts() {
    // Entity Distribution chart
    const entityDistCtx = document.getElementById('entity-distribution-chart').getContext('2d');
    entityDistributionChart = new Chart(entityDistCtx, {
        type: 'pie',
        data: {
            labels: [],
            datasets: [{
                data: [],
                backgroundColor: [
                    'rgba(255, 99, 132, 0.7)',
                    'rgba(54, 162, 235, 0.7)',
                    'rgba(255, 206, 86, 0.7)',
                    'rgba(75, 192, 192, 0.7)',
                    'rgba(153, 102, 255, 0.7)',
                    'rgba(255, 159, 64, 0.7)',
                    'rgba(199, 199, 199, 0.7)',
                    'rgba(83, 102, 255, 0.7)',
                    'rgba(40, 159, 64, 0.7)',
                    'rgba(210, 199, 199, 0.7)'
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
                    text: 'Entity Distribution by Category'
                }
            }
        }
    });

    // Top Entity Types chart
    const entityTypesCtx = document.getElementById('entity-types-chart').getContext('2d');
    entityTypesChart = new Chart(entityTypesCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Count',
                data: [],
                backgroundColor: 'rgba(75, 192, 192, 0.7)',
                borderColor: 'rgba(75, 192, 192, 1)',
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
                    text: 'Top Entity Types'
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
 * Updates all entity-related charts with the latest data
 * @param {Object} appState Application state containing metrics data and configuration
 */
export function updateEntityCharts(appState) {
    // Fetch entity metrics
    fetch('/api/metrics?prefix=entities')
        .then(response => response.json())
        .then(data => {
            updateEntityDistributionChart(data);
            updateEntityTypesChart(data);
            updateEntityHotspotsTable();
        })
        .catch(error => {
            console.error('Error fetching entity metrics:', error);
        });
}

/**
 * Updates the entity distribution pie chart
 */
function updateEntityDistributionChart(data) {
    // Define categories and initialize counters
    const categories = {
        'Hostile': 0,
        'Passive': 0,
        'Ambient': 0,
        'Items': 0,
        'Players': 0,
        'Vehicles': 0,
        'Projectiles': 0,
        'Other': 0
    };

    // Entity classification maps
    const passiveEntities = ['sheep', 'cow', 'chicken', 'pig', 'rabbit', 'horse', 'donkey', 'mule', 'llama', 'fox', 'bat', 'parrot', 'squid', 'cod', 'salmon', 'turtle', 'bee'];
    const hostileEntities = ['zombie', 'skeleton', 'creeper', 'spider', 'enderman', 'witch', 'slime', 'phantom', 'drowned', 'blaze', 'ghast', 'magma_cube', 'pillager', 'ravager', 'vex', 'evoker'];

    // Process entity metrics
    Object.entries(data).forEach(([key, points]) => {
        if (points.length === 0) return;

        const latestValue = points[points.length - 1].value;

        if (key === 'entities.hostile') categories['Hostile'] = latestValue;
        else if (key === 'entities.passive') categories['Passive'] = latestValue;
        else if (key === 'entities.ambient') categories['Ambient'] = latestValue;
        else if (key === 'entities.items') categories['Items'] = latestValue;
        else if (key === 'entities.players') categories['Players'] = latestValue;
        else if (key === 'entities.vehicles') categories['Vehicles'] = latestValue;
        else if (key === 'entities.projectiles') categories['Projectiles'] = latestValue;
        else if (key.startsWith('entities.types.')) {
            // Extract entity type name
            const entityType = key.replace('entities.types.', '').replace(/\./g, ':').split(':').pop();

            // Classify entity based on type
            if (passiveEntities.includes(entityType)) {
                categories['Passive'] += latestValue;
            } else if (hostileEntities.includes(entityType)) {
                categories['Hostile'] += latestValue;
            } else if (entityType === 'item') {
                categories['Items'] += latestValue;
            } else if (entityType === 'player') {
                categories['Players'] += latestValue;
            } else if (['boat', 'minecart'].some(v => entityType.includes(v))) {
                categories['Vehicles'] += latestValue;
            } else if (['arrow', 'fireball', 'trident'].some(v => entityType.includes(v))) {
                categories['Projectiles'] += latestValue;
            } else {
                categories['Other'] += latestValue;
            }
        }
    });

    // Filter out empty categories
    const filteredCategories = Object.fromEntries(
        Object.entries(categories).filter(([_, value]) => value > 0)
    );

    // Update chart data
    entityDistributionChart.data.labels = Object.keys(filteredCategories);
    entityDistributionChart.data.datasets[0].data = Object.values(filteredCategories);
    entityDistributionChart.update();
}

/**
 * Updates the entity types bar chart
 */
function updateEntityTypesChart(data) {
    // Extract entity types
    const entityTypes = {};

    Object.entries(data).forEach(([key, points]) => {
        if (!key.startsWith('entities.types.') || points.length === 0) return;

        const entityType = key.replace('entities.types.', '').replace(/\./g, ':');
        const count = points[points.length - 1].value;

        if (count > 0) {
            entityTypes[entityType] = count;
        }
    });

    // Sort entity types by count and take top 10
    const sortedTypes = Object.entries(entityTypes)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10);

    // Update chart data
    entityTypesChart.data.labels = sortedTypes.map(([type, _]) => formatEntityType(type));
    entityTypesChart.data.datasets[0].data = sortedTypes.map(([_, count]) => count);
    entityTypesChart.update();
}

/**
 * Updates the entity hotspots table with the latest data from the API
 */
function updateEntityHotspotsTable() {
    // Fetch entity hotspots
    fetch('/api/hotspots')
        .then(response => response.json())
        .then(data => {
            console.log('Entity hotspots data:', data);
            const table = document.getElementById('entity-hotspots-table').querySelector('tbody');
            table.innerHTML = '';

            // Create a placeholder message if no data
            if (!data || !data.dimensions || Object.keys(data.dimensions).length === 0) {
                const row = document.createElement('tr');
                row.innerHTML = `<td colspan="4">No entity hotspots detected. This might be populated as more entity data is collected.</td>`;
                table.appendChild(row);
                return;
            }

            // Process hotspots by dimension
            const allHotspots = [];

            // Collect all hotspots
            Object.entries(data.dimensions).forEach(([dimension, positions]) => {
                Object.entries(positions).forEach(([posKey, details]) => {
                    const [x, z] = posKey.split(',').map(Number);
                    const entityCount = details.total || 0;

                    if (entityCount > 0) {
                        allHotspots.push({
                            dimension,
                            x,
                            z,
                            entityCount,
                            details
                        });
                    }
                });
            });

            // If no hotspots found, show a message
            if (allHotspots.length === 0) {
                const row = document.createElement('tr');
                row.innerHTML = `<td colspan="4">No entity hotspots detected. This might be populated as more entity data is collected.</td>`;
                table.appendChild(row);
                return;
            }

            // Sort by entity count (descending) and take top 15
            allHotspots.sort((a, b) => b.entityCount - a.entityCount)
                .slice(0, 15)
                .forEach(hotspot => {
                    const row = document.createElement('tr');

                    // Format dimension name (e.g., minecraft:overworld -> Overworld)
                    let dimensionName = hotspot.dimension
                        .split(':')
                        .pop()
                        .replace(/_/g, ' ');
                    dimensionName = dimensionName.charAt(0).toUpperCase() + dimensionName.slice(1);

                    // Create table row
                    row.innerHTML = `
                        <td>${dimensionName}</td>
                        <td>${hotspot.x}, ${hotspot.z}</td>
                        <td>${hotspot.entityCount}</td>
                        <td>${formatHotspotDetails(hotspot.details)}</td>
                    `;

                    table.appendChild(row);
                });
        })
        .catch(error => {
            console.error('Error fetching entity hotspots:', error);

            // Display error message in table
            const table = document.getElementById('entity-hotspots-table').querySelector('tbody');
            table.innerHTML = '';
            const row = document.createElement('tr');
            row.innerHTML = `<td colspan="4">Error loading entity hotspots data. Check console for details.</td>`;
            table.appendChild(row);
        });
}

/**
 * Formats hotspot details for display in the table
 */
function formatHotspotDetails(details) {
    // Format hotspot details for display
    const excludeKeys = ['x', 'z', 'total'];
    const entityTypes = Object.entries(details)
        .filter(([key, value]) => !excludeKeys.includes(key) && value > 0)
        .sort((a, b) => b[1] - a[1])
        .map(([type, count]) => `${formatEntityType(type)}: ${count}`)
        .slice(0, 3)
        .join(', ');

    return entityTypes || 'N/A';
}