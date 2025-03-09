// Overview tab JavaScript - Handles the main dashboard charts
import { formatTime, formatNumber, getLatestMetricValue } from '../utils/chart-utils.js';
import { filterMetricsByTimeRange, appState } from '../main.js';

// Global chart variables for overview tab
let performanceChart = null;
let memoryChart = null;
let entitiesChunksChart = null;

/**
 * Initializes all overview tab charts
 */
export function initOverviewCharts() {
    // Server Performance chart
    const perfCtx = document.getElementById('performance-chart').getContext('2d');
    performanceChart = new Chart(perfCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'TPS',
                borderColor: 'rgba(54, 162, 235, 1)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                data: [],
                tension: 0.2,
                yAxisID: 'y'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: false,
                    min: 0,
                    max: 20,
                    title: {
                        display: true,
                        text: 'Value'
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

    // Memory Usage chart
    const memoryCtx = document.getElementById('memory-chart').getContext('2d');
    memoryChart = new Chart(memoryCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Heap Used',
                    borderColor: 'rgba(255, 99, 132, 1)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    data: [],
                    tension: 0.2
                },
                {
                    label: 'Heap Committed',
                    borderColor: 'rgba(54, 162, 235, 1)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    data: [],
                    borderDash: [5, 5],
                    tension: 0.2,
                    fill: false
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
                        text: 'Memory (MB)'
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

    // Entities & Chunks chart
    const entitiesChunksCtx = document.getElementById('entities-chunks-chart').getContext('2d');
    entitiesChunksChart = new Chart(entitiesChunksCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [{
                label: 'Entities',
                borderColor: 'rgba(153, 102, 255, 1)',
                backgroundColor: 'rgba(153, 102, 255, 0.2)',
                data: [],
                tension: 0.2,
                yAxisID: 'y'
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    title: {
                        display: true,
                        text: 'Count'
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

    // Setup performance chart tab buttons
    document.querySelectorAll('#overview-tab .chart-tabs button').forEach(button => {
        button.addEventListener('click', function() {
            const parent = this.closest('.chart-wrapper');
            const metricName = this.getAttribute('data-metric');

            // Remove active class from all buttons in this group
            parent.querySelectorAll('.chart-tabs button').forEach(b => b.classList.remove('active'));

            // Add active class to clicked button
            this.classList.add('active');

            // Update the appropriate chart based on the wrapper
            if (parent.querySelector('h2').textContent === 'Server Performance') {
                appState.activePerformanceMetric = metricName;
                updatePerformanceChart(appState);
            } else if (parent.querySelector('h2').textContent === 'Entities & Chunks') {
                appState.activeEntityChunkMetric = metricName;
                updateEntitiesChunksChart(appState);
            }
        });
    });
}

/**
 * Updates all overview charts with the latest data
 * @param {Object} appState Application state containing metrics data and configuration
 */
export function updateOverviewCharts(appState) {
    updatePerformanceChart(appState);
    updateMemoryChart(appState);
    updateEntitiesChunksChart(appState);
}

/**
 * Updates the Server Performance chart
 */
function updatePerformanceChart(appState) {
    const metricName = appState.activePerformanceMetric; // 'server.tps' or 'server.tick_time'
    if (!appState.currentMetrics[metricName]) return;

    const data = filterMetricsByTimeRange(appState.currentMetrics[metricName]);
    if (data.length === 0) return;

    // Prepare chart data
    const timestamps = data.map(point => formatTime(point.timestamp));
    const values = data.map(point => point.value);

    // Update chart
    performanceChart.data.labels = timestamps;
    performanceChart.data.datasets[0].data = values;

    // Update chart label and scale based on which metric is shown
    if (metricName === 'server.tps') {
        performanceChart.data.datasets[0].label = 'TPS';
        performanceChart.options.scales.y.max = 20;
    } else {
        performanceChart.data.datasets[0].label = 'MSPT';
        // Dynamically set max value based on data with some headroom
        const maxValue = Math.max(...values) * 1.2;
        performanceChart.options.scales.y.max = maxValue < 50 ? 50 : maxValue;
    }

    performanceChart.update();
}

/**
 * Updates the Memory Usage chart
 */
function updateMemoryChart(appState) {
    if (!appState.currentMetrics['memory.heap.used'] || !appState.currentMetrics['memory.heap.committed']) return;

    const heapUsedData = filterMetricsByTimeRange(appState.currentMetrics['memory.heap.used']);
    const heapCommittedData = filterMetricsByTimeRange(appState.currentMetrics['memory.heap.committed']);

    if (heapUsedData.length === 0) return;

    // Get timestamps from heap used data
    const timestamps = heapUsedData.map(point => formatTime(point.timestamp));

    // Update chart
    memoryChart.data.labels = timestamps;
    memoryChart.data.datasets[0].data = heapUsedData.map(point => point.value);
    memoryChart.data.datasets[1].data = heapCommittedData.map(point => point.value);
    memoryChart.update();
}

/**
 * Updates the Entities & Chunks chart
 */
function updateEntitiesChunksChart(appState) {
    const metricName = appState.activeEntityChunkMetric; // 'entities.total' or 'chunks.loaded'
    if (!appState.currentMetrics[metricName]) return;

    const data = filterMetricsByTimeRange(appState.currentMetrics[metricName]);
    if (data.length === 0) return;

    // Prepare chart data
    const timestamps = data.map(point => formatTime(point.timestamp));
    const values = data.map(point => point.value);

    // Update chart
    entitiesChunksChart.data.labels = timestamps;
    entitiesChunksChart.data.datasets[0].data = values;

    // Update chart label based on which metric is shown
    entitiesChunksChart.data.datasets[0].label = metricName === 'entities.total' ? 'Entities' : 'Chunks';

    entitiesChunksChart.update();
}