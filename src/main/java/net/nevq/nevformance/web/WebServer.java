package net.nevq.nevformance.web;

import net.nevq.nevformance.Nevformance;
import net.nevq.nevformance.metrics.MetricPoint;
import net.nevq.nevformance.metrics.MetricsManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {
    private static final Logger LOGGER = LoggerFactory.getLogger("nevformance");
    private final int port;
    private final MetricsManager metricsManager;
    private HttpServer server;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Hold static web resources in memory
    private final Map<String, byte[]> staticResources = new HashMap<>();

    public WebServer(int port, MetricsManager metricsManager) {
        this.port = port;
        this.metricsManager = metricsManager;
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(10));

            // API endpoints
            server.createContext("/api/metrics", new MetricsHandler());
            server.createContext("/api/config", new ConfigHandler());

            // Static web resources
            loadStaticResources();
            server.createContext("/", new StaticResourceHandler());

            server.start();
            LOGGER.info("Web server started on port {}", port);
        } catch (Exception e) {
            LOGGER.error("Failed to start web server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("Web server stopped");
        }
    }

    private void loadStaticResources() {
        // These resources would typically be packaged with your mod
        // For now, we'll generate them dynamically

        // HTML
        String indexHtml = generateIndexHtml();
        staticResources.put("index.html", indexHtml.getBytes(StandardCharsets.UTF_8));

        // JavaScript
        String mainJs = generateMainJs();
        staticResources.put("main.js", mainJs.getBytes(StandardCharsets.UTF_8));

        // CSS
        String mainCss = generateMainCss();
        staticResources.put("main.css", mainCss.getBytes(StandardCharsets.UTF_8));

        LOGGER.info("Static web resources loaded");
    }

    private String generateIndexHtml() {
        return "<!DOCTYPE html>\n" +
                "<html lang=\"en\">\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>Nevformance - Minecraft Performance Analytics</title>\n" +
                "    <link rel=\"stylesheet\" href=\"main.css\">\n" +
                "    <script src=\"https://cdn.jsdelivr.net/npm/chart.js\"></script>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <header>\n" +
                "        <h1>Nevformance Analytics</h1>\n" +
                "        <div class=\"server-info\">\n" +
                "            <div class=\"info-item\">\n" +
                "                <span class=\"label\">TPS:</span>\n" +
                "                <span id=\"current-tps\" class=\"value\">-</span>\n" +
                "            </div>\n" +
                "            <div class=\"info-item\">\n" +
                "                <span class=\"label\">MSPT:</span>\n" +
                "                <span id=\"current-mspt\" class=\"value\">-</span>\n" +
                "            </div>\n" +
                "            <div class=\"info-item\">\n" +
                "                <span class=\"label\">Memory:</span>\n" +
                "                <span id=\"current-memory\" class=\"value\">-</span>\n" +
                "            </div>\n" +
                "            <div class=\"info-item\">\n" +
                "                <span class=\"label\">Entities:</span>\n" +
                "                <span id=\"current-entities\" class=\"value\">-</span>\n" +
                "            </div>\n" +
                "            <div class=\"info-item\">\n" +
                "                <span class=\"label\">Chunks:</span>\n" +
                "                <span id=\"current-chunks\" class=\"value\">-</span>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </header>\n" +
                "    \n" +
                "    <div class=\"controls\">\n" +
                "        <div class=\"time-range\">\n" +
                "            <button data-range=\"5m\">5m</button>\n" +
                "            <button data-range=\"15m\">15m</button>\n" +
                "            <button data-range=\"30m\">30m</button>\n" +
                "            <button data-range=\"1h\" class=\"active\">1h</button>\n" +
                "        </div>\n" +
                "        <div class=\"refresh-control\">\n" +
                "            <label for=\"auto-refresh\">Auto-refresh:</label>\n" +
                "            <input type=\"checkbox\" id=\"auto-refresh\" checked>\n" +
                "            <select id=\"refresh-interval\">\n" +
                "                <option value=\"1000\">1s</option>\n" +
                "                <option value=\"5000\" selected>5s</option>\n" +
                "                <option value=\"10000\">10s</option>\n" +
                "                <option value=\"30000\">30s</option>\n" +
                "            </select>\n" +
                "            <button id=\"refresh-now\">Refresh Now</button>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"charts-container\">\n" +
                "        <div class=\"chart-wrapper\">\n" +
                "            <h2>Server Performance</h2>\n" +
                "            <div class=\"chart-tabs\">\n" +
                "                <button data-metric=\"tps\" class=\"active\">TPS</button>\n" +
                "                <button data-metric=\"mspt\">MSPT</button>\n" +
                "            </div>\n" +
                "            <div class=\"chart\">\n" +
                "                <canvas id=\"performance-chart\"></canvas>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"chart-wrapper\">\n" +
                "            <h2>Memory Usage</h2>\n" +
                "            <div class=\"chart\">\n" +
                "                <canvas id=\"memory-chart\"></canvas>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "        \n" +
                "        <div class=\"chart-wrapper\">\n" +
                "            <h2>Entities & Chunks</h2>\n" +
                "            <div class=\"chart-tabs\">\n" +
                "                <button data-metric=\"entities.total\" class=\"active\">Entities</button>\n" +
                "                <button data-metric=\"chunks.loaded\">Chunks</button>\n" +
                "            </div>\n" +
                "            <div class=\"chart\">\n" +
                "                <canvas id=\"entities-chunks-chart\"></canvas>\n" +
                "            </div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <script src=\"main.js\"></script>\n" +
                "</body>\n" +
                "</html>";
    }

    private String generateMainJs() {
        return "// Main JavaScript for Server Analytics\n" +
                "\n" +
                "// Global variables\n" +
                "let performanceChart = null;\n" +
                "let memoryChart = null;\n" +
                "let entitiesChunksChart = null;\n" +
                "let currentMetrics = {};\n" +
                "let refreshInterval = 5000;\n" +
                "let autoRefresh = true;\n" +
                "let refreshTimer = null;\n" +
                "let selectedTimeRange = '1h'; // Default to 1 hour\n" +
                "let activePerformanceMetric = 'tps';\n" +
                "let activeEntityChunkMetric = 'entities.total';\n" +
                "\n" +
                "// Initialize the page\n" +
                "document.addEventListener('DOMContentLoaded', function() {\n" +
                "    initCharts();\n" +
                "    setupEventListeners();\n" +
                "    refreshData();\n" +
                "    startAutoRefresh();\n" +
                "});\n" +
                "\n" +
                "function initCharts() {\n" +
                "    // Performance chart (TPS/MSPT)\n" +
                "    const perfCtx = document.getElementById('performance-chart').getContext('2d');\n" +
                "    performanceChart = new Chart(perfCtx, {\n" +
                "        type: 'line',\n" +
                "        data: {\n" +
                "            labels: [],\n" +
                "            datasets: [{\n" +
                "                label: 'TPS',\n" +
                "                borderColor: 'rgb(75, 192, 192)',\n" +
                "                backgroundColor: 'rgba(75, 192, 192, 0.2)',\n" +
                "                data: [],\n" +
                "                tension: 0.2\n" +
                "            }]\n" +
                "        },\n" +
                "        options: {\n" +
                "            responsive: true,\n" +
                "            maintainAspectRatio: false,\n" +
                "            scales: {\n" +
                "                y: {\n" +
                "                    beginAtZero: false,\n" +
                "                    min: 0,\n" +
                "                    max: 20,\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'TPS'\n" +
                "                    }\n" +
                "                },\n" +
                "                x: {\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'Time'\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "    \n" +
                "    // Memory chart\n" +
                "    const memCtx = document.getElementById('memory-chart').getContext('2d');\n" +
                "    memoryChart = new Chart(memCtx, {\n" +
                "        type: 'line',\n" +
                "        data: {\n" +
                "            labels: [],\n" +
                "            datasets: [{\n" +
                "                label: 'Used Memory (MB)',\n" +
                "                borderColor: 'rgb(255, 99, 132)',\n" +
                "                backgroundColor: 'rgba(255, 99, 132, 0.2)',\n" +
                "                data: [],\n" +
                "                tension: 0.2\n" +
                "            }, {\n" +
                "                label: 'Max Memory (MB)',\n" +
                "                borderColor: 'rgb(54, 162, 235)',\n" +
                "                backgroundColor: 'rgba(54, 162, 235, 0.2)',\n" +
                "                data: [],\n" +
                "                tension: 0.2\n" +
                "            }]\n" +
                "        },\n" +
                "        options: {\n" +
                "            responsive: true,\n" +
                "            maintainAspectRatio: false,\n" +
                "            scales: {\n" +
                "                y: {\n" +
                "                    beginAtZero: false,\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'Memory (MB)'\n" +
                "                    }\n" +
                "                },\n" +
                "                x: {\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'Time'\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "    \n" +
                "    // Entities/Chunks chart\n" +
                "    const entChunksCtx = document.getElementById('entities-chunks-chart').getContext('2d');\n" +
                "    entitiesChunksChart = new Chart(entChunksCtx, {\n" +
                "        type: 'line',\n" +
                "        data: {\n" +
                "            labels: [],\n" +
                "            datasets: [{\n" +
                "                label: 'Total Entities',\n" +
                "                borderColor: 'rgb(153, 102, 255)',\n" +
                "                backgroundColor: 'rgba(153, 102, 255, 0.2)',\n" +
                "                data: [],\n" +
                "                tension: 0.2\n" +
                "            }]\n" +
                "        },\n" +
                "        options: {\n" +
                "            responsive: true,\n" +
                "            maintainAspectRatio: false,\n" +
                "            scales: {\n" +
                "                y: {\n" +
                "                    beginAtZero: false,\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'Count'\n" +
                "                    }\n" +
                "                },\n" +
                "                x: {\n" +
                "                    title: {\n" +
                "                        display: true,\n" +
                "                        text: 'Time'\n" +
                "                    }\n" +
                "                }\n" +
                "            }\n" +
                "        }\n" +
                "    });\n" +
                "}\n" +
                "\n" +
                "function setupEventListeners() {\n" +
                "    // Time range buttons\n" +
                "    document.querySelectorAll('.time-range button').forEach(button => {\n" +
                "        button.addEventListener('click', function() {\n" +
                "            document.querySelectorAll('.time-range button').forEach(b => b.classList.remove('active'));\n" +
                "            this.classList.add('active');\n" +
                "            selectedTimeRange = this.getAttribute('data-range');\n" +
                "            refreshData();\n" +
                "        });\n" +
                "    });\n" +
                "    \n" +
                "    // Performance metric tabs\n" +
                "    document.querySelectorAll('.chart-tabs button[data-metric]').forEach(button => {\n" +
                "        button.addEventListener('click', function() {\n" +
                "            const metricType = this.parentElement.parentElement.querySelector('h2').textContent;\n" +
                "            const metric = this.getAttribute('data-metric');\n" +
                "            \n" +
                "            // Determine which chart to update\n" +
                "            if (metricType.includes('Performance')) {\n" +
                "                document.querySelectorAll('.chart-tabs button[data-metric=\"tps\"], .chart-tabs button[data-metric=\"mspt\"]')\n" +
                "                    .forEach(b => b.classList.remove('active'));\n" +
                "                this.classList.add('active');\n" +
                "                activePerformanceMetric = metric;\n" +
                "                updatePerformanceChart();\n" +
                "            } else if (metricType.includes('Entities')) {\n" +
                "                document.querySelectorAll('.chart-tabs button[data-metric=\"entities.total\"], .chart-tabs button[data-metric=\"chunks.loaded\"]')\n" +
                "                    .forEach(b => b.classList.remove('active'));\n" +
                "                this.classList.add('active');\n" +
                "                activeEntityChunkMetric = metric;\n" +
                "                updateEntitiesChunksChart();\n" +
                "            }\n" +
                "        });\n" +
                "    });\n" +
                "    \n" +
                "    // Auto-refresh controls\n" +
                "    document.getElementById('auto-refresh').addEventListener('change', function() {\n" +
                "        autoRefresh = this.checked;\n" +
                "        if (autoRefresh) {\n" +
                "            startAutoRefresh();\n" +
                "        } else {\n" +
                "            stopAutoRefresh();\n" +
                "        }\n" +
                "    });\n" +
                "    \n" +
                "    document.getElementById('refresh-interval').addEventListener('change', function() {\n" +
                "        refreshInterval = parseInt(this.value, 10);\n" +
                "        if (autoRefresh) {\n" +
                "            stopAutoRefresh();\n" +
                "            startAutoRefresh();\n" +
                "        }\n" +
                "    });\n" +
                "    \n" +
                "    document.getElementById('refresh-now').addEventListener('click', refreshData);\n" +
                "}\n" +
                "\n" +
                "function startAutoRefresh() {\n" +
                "    stopAutoRefresh();\n" +
                "    refreshTimer = setInterval(refreshData, refreshInterval);\n" +
                "}\n" +
                "\n" +
                "function stopAutoRefresh() {\n" +
                "    if (refreshTimer) {\n" +
                "        clearInterval(refreshTimer);\n" +
                "        refreshTimer = null;\n" +
                "    }\n" +
                "}\n" +
                "\n" +
                "function refreshData() {\n" +
                "    fetch('/api/metrics')\n" +
                "        .then(response => response.json())\n" +
                "        .then(data => {\n" +
                "            currentMetrics = data;\n" +
                "            updateCurrentValues();\n" +
                "            updatePerformanceChart();\n" +
                "            updateMemoryChart();\n" +
                "            updateEntitiesChunksChart();\n" +
                "        })\n" +
                "        .catch(error => {\n" +
                "            console.error('Error fetching metrics:', error);\n" +
                "        });\n" +
                "}\n" +
                "\n" +
                "function updateCurrentValues() {\n" +
                "    if (!currentMetrics) return;\n" +
                "    \n" +
                "    // Update the current values display\n" +
                "    const getLatestValue = (metric) => {\n" +
                "        const points = currentMetrics[metric];\n" +
                "        return points && points.length > 0 ? points[points.length - 1].value : '-';\n" +
                "    };\n" +
                "    \n" +
                "    document.getElementById('current-tps').textContent = formatNumber(getLatestValue('tps'), 1);\n" +
                "    document.getElementById('current-mspt').textContent = formatNumber(getLatestValue('mspt'), 1) + ' ms';\n" +
                "    \n" +
                "    const usedMemory = getLatestValue('memory.used');\n" +
                "    const maxMemory = getLatestValue('memory.max');\n" +
                "    document.getElementById('current-memory').textContent = \n" +
                "        `${formatNumber(usedMemory, 0)} / ${formatNumber(maxMemory, 0)} MB`;\n" +
                "    \n" +
                "    document.getElementById('current-entities').textContent = formatNumber(getLatestValue('entities.total'), 0);\n" +
                "    document.getElementById('current-chunks').textContent = formatNumber(getLatestValue('chunks.loaded'), 0);\n" +
                "}\n" +
                "\n" +
                "function updatePerformanceChart() {\n" +
                "    if (!currentMetrics || !currentMetrics[activePerformanceMetric]) return;\n" +
                "    \n" +
                "    const metric = activePerformanceMetric;\n" +
                "    const filteredData = filterDataByTimeRange(currentMetrics[metric]);\n" +
                "    \n" +
                "    performanceChart.data.labels = filteredData.map(point => formatTime(point.timestamp));\n" +
                "    performanceChart.data.datasets[0].data = filteredData.map(point => point.value);\n" +
                "    performanceChart.data.datasets[0].label = metric === 'tps' ? 'TPS' : 'MSPT (ms)';\n" +
                "    \n" +
                "    // Adjust scale based on metric\n" +
                "    if (metric === 'tps') {\n" +
                "        performanceChart.options.scales.y.max = 20;\n" +
                "        performanceChart.options.scales.y.title.text = 'TPS';\n" +
                "    } else {\n" +
                "        // For MSPT, use dynamic scaling based on the data\n" +
                "        performanceChart.options.scales.y.max = undefined;\n" +
                "        performanceChart.options.scales.y.title.text = 'MSPT (ms)';\n" +
                "    }\n" +
                "    \n" +
                "    performanceChart.update();\n" +
                "}\n" +
                "\n" +
                "function updateMemoryChart() {\n" +
                "    if (!currentMetrics || !currentMetrics['memory.used'] || !currentMetrics['memory.max']) return;\n" +
                "    \n" +
                "    const usedMemory = filterDataByTimeRange(currentMetrics['memory.used']);\n" +
                "    const maxMemory = filterDataByTimeRange(currentMetrics['memory.max']);\n" +
                "    \n" +
                "    // Use timestamps from used memory for both datasets to ensure alignment\n" +
                "    memoryChart.data.labels = usedMemory.map(point => formatTime(point.timestamp));\n" +
                "    memoryChart.data.datasets[0].data = usedMemory.map(point => point.value);\n" +
                "    \n" +
                "    // For max memory, we need to match timestamps or interpolate\n" +
                "    // For simplicity, we'll just use the latest max memory value for all points if available\n" +
                "    const latestMaxMemory = maxMemory.length > 0 ? maxMemory[maxMemory.length - 1].value : null;\n" +
                "    memoryChart.data.datasets[1].data = latestMaxMemory ? Array(usedMemory.length).fill(latestMaxMemory) : [];\n" +
                "    \n" +
                "    memoryChart.update();\n" +
                "}\n" +
                "\n" +
                "function updateEntitiesChunksChart() {\n" +
                "    if (!currentMetrics || !currentMetrics[activeEntityChunkMetric]) return;\n" +
                "    \n" +
                "    const metric = activeEntityChunkMetric;\n" +
                "    const filteredData = filterDataByTimeRange(currentMetrics[metric]);\n" +
                "    \n" +
                "    entitiesChunksChart.data.labels = filteredData.map(point => formatTime(point.timestamp));\n" +
                "    entitiesChunksChart.data.datasets[0].data = filteredData.map(point => point.value);\n" +
                "    entitiesChunksChart.data.datasets[0].label = metric === 'entities.total' ? 'Total Entities' : 'Loaded Chunks';\n" +
                "    \n" +
                "    entitiesChunksChart.update();\n" +
                "}\n" +
                "\n" +
                "function filterDataByTimeRange(data) {\n" +
                "    if (!data || data.length === 0) return [];\n" +
                "    \n" +
                "    const now = Date.now();\n" +
                "    let startTime;\n" +
                "    \n" +
                "    switch (selectedTimeRange) {\n" +
                "        case '5m':\n" +
                "            startTime = now - 5 * 60 * 1000;\n" +
                "            break;\n" +
                "        case '15m':\n" +
                "            startTime = now - 15 * 60 * 1000;\n" +
                "            break;\n" +
                "        case '30m':\n" +
                "            startTime = now - 30 * 60 * 1000;\n" +
                "            break;\n" +
                "        case '1h':\n" +
                "        default:\n" +
                "            startTime = now - 60 * 60 * 1000;\n" +
                "            break;\n" +
                "    }\n" +
                "    \n" +
                "    return data.filter(point => point.timestamp >= startTime);\n" +
                "}\n" +
                "\n" +
                "function formatTime(timestamp) {\n" +
                "    const date = new Date(timestamp);\n" +
                "    const hours = date.getHours().toString().padStart(2, '0');\n" +
                "    const minutes = date.getMinutes().toString().padStart(2, '0');\n" +
                "    const seconds = date.getSeconds().toString().padStart(2, '0');\n" +
                "    return `${hours}:${minutes}:${seconds}`;\n" +
                "}\n" +
                "\n" +
                "function formatNumber(value, decimals = 0) {\n" +
                "    if (value === undefined || value === null || value === '-') return '-';\n" +
                "    return Number(value).toFixed(decimals);\n" +
                "}";
    }

    private String generateMainCss() {
        return "/* Main CSS for Server Analytics */\n" +
                "\n" +
                "* {\n" +
                "    box-sizing: border-box;\n" +
                "    margin: 0;\n" +
                "    padding: 0;\n" +
                "}\n" +
                "\n" +
                "body {\n" +
                "    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, 'Open Sans', 'Helvetica Neue', sans-serif;\n" +
                "    background-color: #f5f5f5;\n" +
                "    color: #333;\n" +
                "    padding: 20px;\n" +
                "    line-height: 1.6;\n" +
                "}\n" +
                "\n" +
                "header {\n" +
                "    background-color: #fff;\n" +
                "    border-radius: 8px;\n" +
                "    padding: 20px;\n" +
                "    margin-bottom: 20px;\n" +
                "    box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n" +
                "}\n" +
                "\n" +
                "h1 {\n" +
                "    font-size: 24px;\n" +
                "    margin-bottom: 16px;\n" +
                "    color: #2c3e50;\n" +
                "}\n" +
                "\n" +
                "h2 {\n" +
                "    font-size: 18px;\n" +
                "    margin-bottom: 12px;\n" +
                "    color: #2c3e50;\n" +
                "}\n" +
                "\n" +
                ".server-info {\n" +
                "    display: flex;\n" +
                "    flex-wrap: wrap;\n" +
                "    gap: 16px;\n" +
                "}\n" +
                "\n" +
                ".info-item {\n" +
                "    background-color: #f8f9fa;\n" +
                "    border-radius: 4px;\n" +
                "    padding: 8px 12px;\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "}\n" +
                "\n" +
                ".label {\n" +
                "    font-weight: 600;\n" +
                "    margin-right: 8px;\n" +
                "}\n" +
                "\n" +
                ".value {\n" +
                "    font-family: monospace;\n" +
                "}\n" +
                "\n" +
                ".controls {\n" +
                "    display: flex;\n" +
                "    justify-content: space-between;\n" +
                "    background-color: #fff;\n" +
                "    border-radius: 8px;\n" +
                "    padding: 16px;\n" +
                "    margin-bottom: 20px;\n" +
                "    box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n" +
                "}\n" +
                "\n" +
                ".time-range, .refresh-control {\n" +
                "    display: flex;\n" +
                "    align-items: center;\n" +
                "    gap: 8px;\n" +
                "}\n" +
                "\n" +
                "button {\n" +
                "    background-color: #eee;\n" +
                "    border: none;\n" +
                "    border-radius: 4px;\n" +
                "    padding: 6px 12px;\n" +
                "    cursor: pointer;\n" +
                "    transition: background-color 0.2s;\n" +
                "}\n" +
                "\n" +
                "button:hover {\n" +
                "    background-color: #ddd;\n" +
                "}\n" +
                "\n" +
                "button.active {\n" +
                "    background-color: #3498db;\n" +
                "    color: white;\n" +
                "}\n" +
                "\n" +
                "select, input {\n" +
                "    padding: 6px;\n" +
                "    border: 1px solid #ddd;\n" +
                "    border-radius: 4px;\n" +
                "}\n" +
                "\n" +
                "select:focus, input:focus {\n" +
                "    outline: none;\n" +
                "    border-color: #3498db;\n" +
                "}\n" +
                "\n" +
                ".charts-container {\n" +
                "    display: grid;\n" +
                "    grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));\n" +
                "    gap: 20px;\n" +
                "}\n" +
                "\n" +
                ".chart-wrapper {\n" +
                "    background-color: #fff;\n" +
                "    border-radius: 8px;\n" +
                "    padding: 20px;\n" +
                "    box-shadow: 0 2px 4px rgba(0,0,0,0.1);\n" +
                "}\n" +
                "\n" +
                ".chart-tabs {\n" +
                "    display: flex;\n" +
                "    gap: 8px;\n" +
                "    margin-bottom: 12px;\n" +
                "}\n" +
                "\n" +
                ".chart {\n" +
                "    height: 300px;\n" +
                "    position: relative;\n" +
                "}\n" +
                "\n" +
                "@media (max-width: 768px) {\n" +
                "    .charts-container {\n" +
                "        grid-template-columns: 1fr;\n" +
                "    }\n" +
                "    \n" +
                "    .controls {\n" +
                "        flex-direction: column;\n" +
                "        gap: 16px;\n" +
                "    }\n" +
                "    \n" +
                "    .server-info {\n" +
                "        flex-direction: column;\n" +
                "    }\n" +
                "}";
    }

    // HTTP request handlers

    class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Get all metrics
                Map<String, List<MetricPoint>> metrics = metricsManager.getMetrics();

                // Convert to JSON
                String jsonResponse = gson.toJson(metrics);

                // Send the response
                sendResponse(exchange, 200, jsonResponse, "application/json");
            } catch (Exception e) {
                LOGGER.error("Error handling metrics request", e);
                sendResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // For now, this is just a placeholder
            // In a more advanced version, this could allow configuring the mod through the web interface
            sendResponse(exchange, 200, "{\"status\":\"not_implemented\"}", "application/json");
        }
    }

    class StaticResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Default to index.html for root path
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Remove leading slash
            path = path.substring(1);

            // Check if the resource exists
            byte[] content = staticResources.get(path);
            if (content == null) {
                sendResponse(exchange, 404, "Not Found");
                return;
            }

            // Determine content type
            String contentType = "text/plain";
            if (path.endsWith(".html")) {
                contentType = "text/html";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript";
            } else if (path.endsWith(".css")) {
                contentType = "text/css";
            }

            // Send the response
            sendResponse(exchange, 200, content, contentType);
        }
    }

    // Helper method to send responses
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        sendResponse(exchange, statusCode, response.getBytes(StandardCharsets.UTF_8), "text/plain");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        sendResponse(exchange, statusCode, response.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private void sendResponse(HttpExchange exchange, int statusCode, byte[] response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }
}