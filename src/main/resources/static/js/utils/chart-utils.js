// Utility functions for charts and data handling

/**
 * Filters data points by selected time range
 * @param {Array} data Array of metric points with timestamp and value
 * @param {string} timeRange Time range to filter by ('5m', '15m', '30m', '1h')
 * @returns {Array} Filtered data points
 */
export function filterDataByTimeRange(data, timeRange) {
    if (!data || data.length === 0) return [];

    const now = Date.now();
    let startTime;

    switch (timeRange) {
        case '5m':
            startTime = now - 5 * 60 * 1000;
            break;
        case '15m':
            startTime = now - 15 * 60 * 1000;
            break;
        case '30m':
            startTime = now - 30 * 60 * 1000;
            break;
        case '1h':
        default:
            startTime = now - 60 * 60 * 1000;
            break;
    }

    return data.filter(point => point.timestamp >= startTime);
}

/**
 * Formats a timestamp into a readable time string
 * @param {number} timestamp Timestamp in milliseconds
 * @returns {string} Formatted time (HH:MM:SS)
 */
export function formatTime(timestamp) {
    const date = new Date(timestamp);
    const hours = date.getHours().toString().padStart(2, '0');
    const minutes = date.getMinutes().toString().padStart(2, '0');
    const seconds = date.getSeconds().toString().padStart(2, '0');
    return `${hours}:${minutes}:${seconds}`;
}

/**
 * Formats a number with specified decimal places
 * @param {number|string} value The value to format
 * @param {number} decimals Number of decimal places
 * @returns {string} Formatted number
 */
export function formatNumber(value, decimals = 0) {
    if (value === undefined || value === null || value === '-') return '-';
    return Number(value).toFixed(decimals);
}

/**
 * Gets the latest value from a metric time series
 * @param {Object} metricsData Object containing metric time series
 * @param {string} metric Metric name
 * @returns {number|string} Latest value or '-' if not available
 */
export function getLatestMetricValue(metricsData, metric) {
    const points = metricsData[metric];
    return points && points.length > 0 ? points[points.length - 1].value : '-';
}

/**
 * Formats a dimension name for display (e.g., minecraft.overworld -> Overworld)
 * @param {string} dimension Dimension identifier string
 * @returns {string} Formatted dimension name
 */
export function formatDimensionName(dimension) {
    // Format dimension name (e.g., minecraft.overworld -> Overworld)
    const parts = dimension.split('.');
    if (parts.length < 2) return dimension;

    const name = parts[1].replace(/_/g, ' ');
    return name.charAt(0).toUpperCase() + name.slice(1);
}

/**
 * Formats a block entity type for display (e.g., minecraft:furnace -> Furnace)
 * @param {string} type Block entity type identifier
 * @returns {string} Formatted type name
 */
export function formatBlockEntityType(type) {
    // Format block entity type (e.g., minecraft:furnace -> Furnace)
    return type.split(':').pop()
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}

/**
 * Formats an entity type for display
 * @param {string} type Entity type identifier
 * @returns {string} Formatted entity type name
 */
export function formatEntityType(type) {
    // Format entity type for display (e.g., minecraft:cow -> Cow)
    return type.split(':').pop()
        .split('_')
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}