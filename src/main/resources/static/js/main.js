// Nevformance - Main JavaScript Module

// Import utility functions and tab-specific modules
import { filterDataByTimeRange, formatTime, formatNumber, getLatestMetricValue } from '/js/utils/chart-utils.js';
import { initOverviewCharts, updateOverviewCharts } from './modules/overview-tab.js';
import { initAnalyticsCharts, updateAnalyticsCharts } from './modules/analytics-tab.js';
import { initChunkCharts, updateChunkCharts } from './modules/chunks-tab.js';
import { initEntityCharts, updateEntityCharts } from './modules/entities-tab.js';
import { initSystemCharts, updateSystemCharts } from './modules/system-tab.js';

// Global application state
const appState = {
    currentMetrics: {},
    refreshInterval: 5000,
    autoRefresh: true,
    refreshTimer: null,
    selectedTimeRange: '1h',
    activePerformanceMetric: 'server.tps',
    activeEntityChunkMetric: 'entities.total'
};

// Initialize the application when DOM is fully loaded
document.addEventListener('DOMContentLoaded', () => {
    // Initialize core charts for each tab
    initCoreCharts();

    // Setup event listeners
    setupGlobalEventListeners();

    // Load initial data
    refreshData();

    // Start auto-refresh
    startAutoRefresh();
});

// Initialize charts across different tabs
function initCoreCharts() {
    const chartInitFunctions = [
        initOverviewCharts,
        initAnalyticsCharts,
        initChunkCharts,
        initEntityCharts,
        initSystemCharts
    ];

    chartInitFunctions.forEach(initFunction => {
        if (typeof initFunction === 'function') {
            initFunction();
        }
    });
}

// Setup global event listeners
function setupGlobalEventListeners() {
    // Time range buttons
    document.querySelectorAll('.time-range button').forEach(button => {
        button.addEventListener('click', handleTimeRangeChange);
    });

    // Auto-refresh controls
    document.getElementById('auto-refresh').addEventListener('change', handleAutoRefreshToggle);
    document.getElementById('refresh-interval').addEventListener('change', handleRefreshIntervalChange);
    document.getElementById('refresh-now').addEventListener('click', refreshData);

    // Tab switching
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', handleTabSwitch);
    });
}

// Event handler for time range selection
function handleTimeRangeChange(event) {
    // Remove active class from all buttons
    document.querySelectorAll('.time-range button').forEach(b => b.classList.remove('active'));

    // Add active class to clicked button
    event.target.classList.add('active');

    // Update selected time range
    appState.selectedTimeRange = event.target.getAttribute('data-range');

    // Refresh data to reflect new time range
    refreshData();
}

// Event handler for auto-refresh toggle
function handleAutoRefreshToggle(event) {
    appState.autoRefresh = event.target.checked;

    if (appState.autoRefresh) {
        startAutoRefresh();
    } else {
        stopAutoRefresh();
    }
}

// Event handler for refresh interval change
function handleRefreshIntervalChange(event) {
    appState.refreshInterval = parseInt(event.target.value, 10);

    if (appState.autoRefresh) {
        stopAutoRefresh();
        startAutoRefresh();
    }
}

// Event handler for tab switching
function handleTabSwitch(event) {
    const tabId = event.target.getAttribute('data-tab');

    // Remove active class from all tab buttons and content
    document.querySelectorAll('.tab-button').forEach(b => b.classList.remove('active'));
    document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

    // Activate selected tab
    event.target.classList.add('active');
    document.getElementById(`${tabId}-tab`).classList.add('active');
}

// Start auto-refresh of metrics
function startAutoRefresh() {
    stopAutoRefresh();
    appState.refreshTimer = setInterval(refreshData, appState.refreshInterval);
}

// Stop auto-refresh
function stopAutoRefresh() {
    if (appState.refreshTimer) {
        clearInterval(appState.refreshTimer);
        appState.refreshTimer = null;
    }
}

// Fetch and update metrics data
function refreshData() {
    fetch('/api/metrics')
        .then(response => response.json())
        .then(data => {
            // Debug: log the first metrics data received
            console.log('Received metrics data:', Object.keys(data).length, 'metrics found');
            console.log('Sample metrics:', Object.keys(data).slice(0, 5));

            // Store current metrics
            appState.currentMetrics = data;

            // Update overview and tab-specific charts
            updateCharts();
        })
        .catch(error => {
            console.error('Error fetching metrics:', error);
        });
}

// Update charts across different tabs
function updateCharts() {
    const updateFunctions = [
        updateCurrentValues,
        updateOverviewCharts,
        updateAnalyticsCharts,
        updateChunkCharts,
        updateEntityCharts,
        updateSystemCharts
    ];

    updateFunctions.forEach(updateFunction => {
        if (typeof updateFunction === 'function') {
            // Pass appState to tab-specific update functions
            if (updateFunction !== updateCurrentValues) {
                updateFunction(appState);
            } else {
                updateFunction();
            }
        }
    });
}

// Update current overview values
function updateCurrentValues() {
    if (!appState.currentMetrics) return;

    // Update key metrics display
    document.getElementById('current-tps').textContent =
        formatNumber(getLatestMetricValue(appState.currentMetrics, 'server.tps'), 1);

    document.getElementById('current-mspt').textContent =
        `${formatNumber(getLatestMetricValue(appState.currentMetrics, 'server.tick_time'), 1)} ms`;

    const usedMemory = getLatestMetricValue(appState.currentMetrics, 'memory.heap.used');
    const maxMemory = getLatestMetricValue(appState.currentMetrics, 'memory.heap.max');

    document.getElementById('current-memory').textContent =
        `${formatNumber(usedMemory, 0)} / ${formatNumber(maxMemory, 0)} MB`;

    document.getElementById('current-entities').textContent =
        formatNumber(getLatestMetricValue(appState.currentMetrics, 'entities.total'), 0);

    document.getElementById('current-chunks').textContent =
        formatNumber(getLatestMetricValue(appState.currentMetrics, 'chunks.loaded'), 0);
}

// Helper function to filter metrics data by current time range
export function filterMetricsByTimeRange(metricData) {
    return filterDataByTimeRange(metricData, appState.selectedTimeRange);
}

// Expose key functions and state for potential external use
export {
    appState,
    refreshData,
    startAutoRefresh,
    stopAutoRefresh
};