// Analytics tab JavaScript - Handles performance analysis charts
import { formatNumber } from '../utils/chart-utils.js';
import { filterMetricsByTimeRange } from '../main.js';

// Global chart variables
let tickDistributionChart = null;
let lagSpikesChart = null;
let correlationChart = null;

/**
 * Initializes all analytics tab charts
 */
export function initAnalyticsCharts() {
    // Performance Distribution chart
    const tickDistCtx = document.getElementById('tick-distribution-chart').getContext('2d');
    tickDistributionChart = new Chart(tickDistCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Frequency',
                data: [],
                backgroundColor: 'rgba(75, 192, 192, 0.7)',
                borderColor: 'rgba(75, 192, 192, 1)',
                borderWidth: 1
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
                        text: 'Frequency'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'MSPT Range'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'MSPT Distribution'
                }
            }
        }
    });

    // Lag Spikes chart
    const lagSpikesCtx = document.getElementById('lag-spikes-chart').getContext('2d');
    lagSpikesChart = new Chart(lagSpikesCtx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [{
                label: 'Lag Spikes',
                data: [],
                backgroundColor: 'rgba(255, 99, 132, 0.7)',
                borderColor: 'rgba(255, 99, 132, 1)',
                borderWidth: 1
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
                        text: 'Time Period'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'Lag Spike Frequency'
                }
            }
        }
    });

    // Correlation Analysis chart
    const correlationCtx = document.getElementById('correlation-chart').getContext('2d');
    correlationChart = new Chart(correlationCtx, {
        type: 'scatter',
        data: {
            datasets: [{
                label: 'Correlation',
                data: [],
                backgroundColor: 'rgba(153, 102, 255, 0.7)',
                borderColor: 'rgba(153, 102, 255, 1)',
                pointRadius: 5,
                pointHoverRadius: 7
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: {
                        display: true,
                        text: 'Y Axis'
                    }
                },
                x: {
                    title: {
                        display: true,
                        text: 'X Axis'
                    }
                }
            },
            plugins: {
                title: {
                    display: true,
                    text: 'Correlation Analysis'
                }
            }
        }
    });

    // Setup correlation update button event listener
    document.getElementById('update-correlation').addEventListener('click', () => updateCorrelationChart());
}

/**
 * Updates all analytics charts with the latest data
 * @param {Object} appState Application state containing metrics data and configuration
 */
export function updateAnalyticsCharts(appState) {
    // Fetch server performance metrics
    Promise.all([
        fetch('/api/metrics?metric=server.tick_time').then(response => response.json()),
        fetch('/api/lagspikes').then(response => response.json())
    ])
        .then(([tickTimeData, lagSpikeData]) => {
            updateTickDistributionChart(tickTimeData);
            updateLagSpikesChart(lagSpikeData);
            // Correlation chart is updated on-demand by user
        })
        .catch(error => {
            console.error('Error fetching analytics metrics:', error);
        });
}

/**
 * Updates the tick distribution chart
 * @param {Object} tickTimeData - Tick time metrics data
 */
function updateTickDistributionChart(tickTimeData) {
    if (!tickTimeData['server.tick_time']) return;

    const tickTimes = tickTimeData['server.tick_time'].map(point => point.value);

    // Create distribution buckets (0-5ms, 5-10ms, 10-15ms, 15-20ms, 20-25ms, 25-50ms, 50+ms)
    const buckets = [
        { label: '0-5ms', min: 0, max: 5, count: 0 },
        { label: '5-10ms', min: 5, max: 10, count: 0 },
        { label: '10-15ms', min: 10, max: 15, count: 0 },
        { label: '15-20ms', min: 15, max: 20, count: 0 },
        { label: '20-25ms', min: 20, max: 25, count: 0 },
        { label: '25-50ms', min: 25, max: 50, count: 0 },
        { label: '50ms+', min: 50, max: Infinity, count: 0 }
    ];

    // Count ticks in each bucket
    tickTimes.forEach(mspt => {
        for (const bucket of buckets) {
            if (mspt >= bucket.min && mspt < bucket.max) {
                bucket.count++;
                break;
            }
        }
    });

    // Update chart
    tickDistributionChart.data.labels = buckets.map(bucket => bucket.label);
    tickDistributionChart.data.datasets[0].data = buckets.map(bucket => bucket.count);
    tickDistributionChart.update();
}

/**
 * Updates the lag spikes chart
 * @param {Object} lagSpikeData - Lag spike metrics data
 */
function updateLagSpikesChart(lagSpikeData) {
    if (!lagSpikeData.lagSpikes) return;

    // Group lag spikes by time period (last minute, 5 minutes, 15 minutes, 30 minutes, 1 hour)
    const now = Date.now();
    const timePeriods = [
        { label: 'Last Minute', timeAgo: 60 * 1000, count: 0 },
        { label: 'Last 5 Minutes', timeAgo: 5 * 60 * 1000, count: 0 },
        { label: 'Last 15 Minutes', timeAgo: 15 * 60 * 1000, count: 0 },
        { label: 'Last 30 Minutes', timeAgo: 30 * 60 * 1000, count: 0 },
        { label: 'Last Hour', timeAgo: 60 * 60 * 1000, count: 0 }
    ];

    // Count lag spikes in each time period
    lagSpikeData.lagSpikes.forEach(spike => {
        const spikeTime = spike.timestamp;

        for (const period of timePeriods) {
            if (now - spikeTime <= period.timeAgo) {
                period.count++;
            }
        }
    });

    // Update chart
    lagSpikesChart.data.labels = timePeriods.map(period => period.label);
    lagSpikesChart.data.datasets[0].data = timePeriods.map(period => period.count);
    lagSpikesChart.update();
}

/**
 * Updates the correlation chart based on user-selected metrics
 */
function updateCorrelationChart() {
    // Get selected metrics for X and Y axes
    const metricX = document.getElementById('metric-x').value;
    const metricY = document.getElementById('metric-y').value;

    // Fetch both metrics
    Promise.all([
        fetch(`/api/metrics?metric=${metricX}`).then(response => response.json()),
        fetch(`/api/metrics?metric=${metricY}`).then(response => response.json())
    ])
        .then(([xData, yData]) => {
            if (!xData[metricX] || !yData[metricY]) return;

            // Create correlation data points
            const xPoints = xData[metricX];
            const yPoints = yData[metricY];

            // Find matching timestamps
            const dataPoints = [];
            xPoints.forEach(xPoint => {
                const matchingYPoint = yPoints.find(yPoint => {
                    // Consider timestamps within 1 second of each other as "matching"
                    return Math.abs(yPoint.timestamp - xPoint.timestamp) < 1000;
                });

                if (matchingYPoint) {
                    dataPoints.push({
                        x: xPoint.value,
                        y: matchingYPoint.value
                    });
                }
            });

            // Update chart
            correlationChart.data.datasets[0].data = dataPoints;

            // Update axis labels
            correlationChart.options.scales.x.title.text = formatMetricName(metricX);
            correlationChart.options.scales.y.title.text = formatMetricName(metricY);

            // Update chart title
            correlationChart.options.plugins.title.text = `Correlation: ${formatMetricName(metricX)} vs ${formatMetricName(metricY)}`;

            correlationChart.update();

            // Calculate and display correlation coefficient
            const correlationCoefficient = calculateCorrelation(dataPoints);
            console.log(`Correlation coefficient: ${correlationCoefficient.toFixed(3)}`);
        })
        .catch(error => {
            console.error('Error updating correlation chart:', error);
        });
}

/**
 * Calculates the Pearson correlation coefficient
 * @param {Array} dataPoints - Array of {x, y} data points
 * @returns {number} Correlation coefficient
 */
function calculateCorrelation(dataPoints) {
    if (dataPoints.length < 2) return 0;

    // Extract x and y values
    const xValues = dataPoints.map(point => point.x);
    const yValues = dataPoints.map(point => point.y);

    // Calculate means
    const xMean = xValues.reduce((sum, x) => sum + x, 0) / xValues.length;
    const yMean = yValues.reduce((sum, y) => sum + y, 0) / yValues.length;

    // Calculate covariance and standard deviations
    let covariance = 0;
    let xVariance = 0;
    let yVariance = 0;

    for (let i = 0; i < dataPoints.length; i++) {
        const xDiff = xValues[i] - xMean;
        const yDiff = yValues[i] - yMean;

        covariance += xDiff * yDiff;
        xVariance += xDiff * xDiff;
        yVariance += yDiff * yDiff;
    }

    covariance /= dataPoints.length;
    xVariance /= dataPoints.length;
    yVariance /= dataPoints.length;

    // Pearson correlation coefficient
    return covariance / (Math.sqrt(xVariance) * Math.sqrt(yVariance));
}

/**
 * Formats a metric name for display (e.g., server.tick_time -> Server Tick Time)
 * @param {string} metric - Metric name to format
 * @returns {string} Formatted metric name
 */
function formatMetricName(metric) {
    return metric
        .split('.')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ')
        .replace(/_/g, ' ');
}