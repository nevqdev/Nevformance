// System tab JavaScript - Handles system resource monitoring charts
import { formatTime } from '../utils/chart-utils.js';
import { filterMetricsByTimeRange } from '../main.js';

// Global chart variables
let cpuChart = null;
let threadsChart = null;
let gcChart = null;
let memoryPoolsChart = null;

/**
 * Initializes all system tab charts
 */
export function initSystemCharts() {
    // CPU Usage chart
    const cpuCtx = document.getElementById('cpu-chart').getContext('2d');
    cpuChart = new Chart(cpuCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'System CPU',
                    borderColor: 'rgba(54, 162, 235, 1)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    data: [],
                    tension: 0.2
                },
                {
                    label: 'Process CPU',
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
                    max: 100,
                    title: {
                        display: true,
                        text: 'CPU Usage (%)'
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

    // Thread Activity chart
    const threadsCtx = document.getElementById('threads-chart').getContext('2d');
    threadsChart = new Chart(threadsCtx, {
        type: 'line',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Active Threads',
                    borderColor: 'rgba(153, 102, 255, 1)',
                    backgroundColor: 'rgba(153, 102, 255, 0.2)',
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
                        text: 'Thread Count'
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

    // Garbage Collection chart
    const gcCtx = document.getElementById('gc-chart').getContext('2d');
    gcChart = new Chart(gcCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Young GC',
                    backgroundColor: 'rgba(75, 192, 192, 0.7)',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    data: []
                },
                {
                    label: 'Old GC',
                    backgroundColor: 'rgba(255, 159, 64, 0.7)',
                    borderColor: 'rgba(255, 159, 64, 1)',
                    data: []
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
                        text: 'GC Rate (events/min)'
                    }
                }
            }
        }
    });

    // Memory Pools chart
    const memoryPoolsCtx = document.getElementById('memory-pools-chart').getContext('2d');
    memoryPoolsChart = new Chart(memoryPoolsCtx, {
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
                    tension: 0.2,
                    fill: false
                },
                {
                    label: 'Non-Heap Used',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
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
}

/**
 * Updates all system tab charts with latest data
 * @param {Object} appState Application state containing metrics data and configuration
 */
export function updateSystemCharts(appState) {
    // Fetch system metrics
    Promise.all([
        fetch('/api/metrics?prefix=cpu').then(response => response.json()),
        fetch('/api/metrics?prefix=threads').then(response => response.json()),
        fetch('/api/metrics?prefix=gc').then(response => response.json()),
        fetch('/api/metrics?prefix=memory').then(response => response.json())
    ])
        .then(([cpuData, threadsData, gcData, memoryData]) => {
            updateCpuChart(cpuData, appState.selectedTimeRange);
            updateThreadsChart(threadsData, appState.selectedTimeRange);
            updateGcChart(gcData, appState.selectedTimeRange);
            updateMemoryPoolsChart(memoryData, appState.selectedTimeRange);
        })
        .catch(error => {
            console.error('Error fetching system metrics:', error);
        });
}

/**
 * Updates the CPU usage chart
 * @param {Object} cpuData CPU metrics data
 * @param {string} timeRange Selected time range
 */
function updateCpuChart(cpuData, timeRange) {
    if (!cpuData['cpu.system'] || !cpuData['cpu.process']) return;

    const systemCpuData = filterMetricsByTimeRange(cpuData['cpu.system']);
    const processCpuData = filterMetricsByTimeRange(cpuData['cpu.process']);

    // Get timestamps from system CPU data
    const timestamps = systemCpuData.map(point => formatTime(point.timestamp));

    // Update chart
    cpuChart.data.labels = timestamps;
    cpuChart.data.datasets[0].data = systemCpuData.map(point => point.value);
    cpuChart.data.datasets[1].data = processCpuData.map(point => point.value);
    cpuChart.update();
}

/**
 * Updates the threads activity chart
 * @param {Object} threadsData Thread metrics data
 * @param {string} timeRange Selected time range
 */
function updateThreadsChart(threadsData, timeRange) {
    if (!threadsData['threads.active']) return;

    const threadsActiveData = filterMetricsByTimeRange(threadsData['threads.active']);

    // Update chart
    threadsChart.data.labels = threadsActiveData.map(point => formatTime(point.timestamp));
    threadsChart.data.datasets[0].data = threadsActiveData.map(point => point.value);
    threadsChart.update();
}

/**
 * Updates the garbage collection chart with more detailed debugging
 * @param {Object} gcData Garbage collection metrics data
 * @param {string} timeRange Selected time range
 */
function updateGcChart(gcData, timeRange) {
    console.log('GC Data full object:', gcData);

    // Check for expected properties
    console.log('Has gc.young.rate:', gcData.hasOwnProperty('gc.young.rate'));
    console.log('Has gc.old.rate:', gcData.hasOwnProperty('gc.old.rate'));

    // Look for similar keys that might be used instead
    const potentialGcKeys = Object.keys(gcData).filter(key =>
        key.toLowerCase().includes('gc') ||
        key.toLowerCase().includes('garbage') ||
        key.toLowerCase().includes('collector')
    );
    console.log('Potential GC-related keys:', potentialGcKeys);

    // Check if both required metrics exist
    const hasYoungGC = gcData['gc.young.rate'] && gcData['gc.young.rate'].length > 0;
    const hasOldGC = gcData['gc.old.rate'] && gcData['gc.old.rate'].length > 0;

    if (!hasYoungGC && !hasOldGC) {
        // No GC data found, try to use alternative keys if available
        if (potentialGcKeys.length > 0) {
            console.log('Using alternative GC keys');
            // Use the first two alternative keys found, if any
            const primaryKey = potentialGcKeys[0];
            const secondaryKey = potentialGcKeys.length > 1 ? potentialGcKeys[1] : null;

            const primaryData = primaryKey ? filterMetricsByTimeRange(gcData[primaryKey]) : [];
            const secondaryData = secondaryKey ? filterMetricsByTimeRange(gcData[secondaryKey]) : [];

            if (primaryData.length > 0) {
                const timestamps = primaryData.map(point => formatTime(point.timestamp));

                // Update chart with alternative data
                gcChart.data.labels = timestamps;
                gcChart.data.datasets[0].label = primaryKey;
                gcChart.data.datasets[0].data = primaryData.map(point => point.value);

                if (secondaryData.length > 0) {
                    gcChart.data.datasets[1].label = secondaryKey;
                    gcChart.data.datasets[1].data = secondaryData.map(point => point.value);
                } else {
                    gcChart.data.datasets[1].data = Array(timestamps.length).fill(0);
                }

                gcChart.update();
                return;
            }
        }

        // Still no data, set placeholder
        console.log('No GC data found, using placeholder');
        gcChart.data.labels = ['No GC Data Available'];
        gcChart.data.datasets[0].data = [0];
        gcChart.data.datasets[1].data = [0];
        gcChart.update();
        return;
    }

    // Process available data
    console.log('Standard GC data found');
    let youngGcData = [];
    let oldGcData = [];
    let timestamps = ['No Data'];

    // Process available data
    if (hasYoungGC) {
        youngGcData = filterMetricsByTimeRange(gcData['gc.young.rate']);
        timestamps = youngGcData.map(point => formatTime(point.timestamp));
        console.log('Young GC data points:', youngGcData.length);
    }

    if (hasOldGC) {
        oldGcData = filterMetricsByTimeRange(gcData['gc.old.rate']);
        if (!hasYoungGC && oldGcData.length > 0) {
            timestamps = oldGcData.map(point => formatTime(point.timestamp));
        }
        console.log('Old GC data points:', oldGcData.length);
    }

    // Update chart with available data
    gcChart.data.labels = timestamps;
    gcChart.data.datasets[0].data = hasYoungGC ? youngGcData.map(point => point.value) : Array(timestamps.length).fill(0);
    gcChart.data.datasets[1].data = hasOldGC ? oldGcData.map(point => point.value) : Array(timestamps.length).fill(0);
    gcChart.update();
}
/**
 * Updates the garbage collection chart
 * @param {Object} gcData Garbage collection metrics data
 * @param {string} timeRange Selected time range

function updateGcChart(gcData, timeRange) {
    console.log('GC Data keys:', Object.keys(gcData));

    // Check if both required metrics exist
    const hasYoungGC = gcData['gc.young.rate'] && gcData['gc.young.rate'].length > 0;
    const hasOldGC = gcData['gc.old.rate'] && gcData['gc.old.rate'].length > 0;

    if (!hasYoungGC && !hasOldGC) {
        // Set a placeholder dataset if no data
        gcChart.data.labels = ['No GC Data'];
        gcChart.data.datasets[0].data = [0];
        gcChart.data.datasets[1].data = [0];
        gcChart.update();
        return;
    }

    // Default empty dataset
    let youngGcData = [];
    let oldGcData = [];
    let timestamps = ['No Data'];

    // Process available data
    if (hasYoungGC) {
        youngGcData = filterMetricsByTimeRange(gcData['gc.young.rate']);
        timestamps = youngGcData.map(point => formatTime(point.timestamp));
    }

    if (hasOldGC) {
        oldGcData = filterMetricsByTimeRange(gcData['gc.old.rate']);
        if (!hasYoungGC && oldGcData.length > 0) {
            timestamps = oldGcData.map(point => formatTime(point.timestamp));
        }
    }

    // Update chart with available data
    gcChart.data.labels = timestamps;
    gcChart.data.datasets[0].data = hasYoungGC ? youngGcData.map(point => point.value) : Array(timestamps.length).fill(0);
    gcChart.data.datasets[1].data = hasOldGC ? oldGcData.map(point => point.value) : Array(timestamps.length).fill(0);
    gcChart.update();
}
*/
/**
 * Updates the memory pools chart
 * @param {Object} memoryData Memory metrics data
 * @param {string} timeRange Selected time range
 */
function updateMemoryPoolsChart(memoryData, timeRange) {
    if (!memoryData['memory.heap.used'] || !memoryData['memory.heap.committed'] || !memoryData['memory.nonheap.used']) return;

    const heapUsedData = filterMetricsByTimeRange(memoryData['memory.heap.used']);
    const heapCommittedData = filterMetricsByTimeRange(memoryData['memory.heap.committed']);
    const nonHeapUsedData = filterMetricsByTimeRange(memoryData['memory.nonheap.used']);

    // Get timestamps from heap used data
    const timestamps = heapUsedData.map(point => formatTime(point.timestamp));

    // Update chart
    memoryPoolsChart.data.labels = timestamps;
    memoryPoolsChart.data.datasets[0].data = heapUsedData.map(point => point.value);
    memoryPoolsChart.data.datasets[1].data = heapCommittedData.map(point => point.value);
    memoryPoolsChart.data.datasets[2].data = nonHeapUsedData.map(point => point.value);
    memoryPoolsChart.update();
}