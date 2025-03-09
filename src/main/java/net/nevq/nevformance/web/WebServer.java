package net.nevq.nevformance.web;

import net.nevq.nevformance.Nevformance;
import net.nevq.nevformance.metrics.MetricPoint;
import net.nevq.nevformance.metrics.MetricsManager;
import net.nevq.nevformance.metrics.collectors.SystemMetricsCollector;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;

public class WebServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(WebServer.class);

    // MIME type mappings for different file types
    private static final Map<String, String> MIME_TYPES = Map.of(
            ".html", "text/html",
            ".css", "text/css",
            ".js", "application/javascript",
            ".json", "application/json",
            ".svg", "image/svg+xml",
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".ico", "image/x-icon"
    );

    private final int port;
    private final MetricsManager metricsManager;
    private HttpServer server;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public WebServer(int port, MetricsManager metricsManager) {
        this.port = port;
        this.metricsManager = metricsManager;
    }

    /**
     * Starts the web server and sets up API and static resource handlers
     */
    public void start() {
        try {
            // Create HTTP server
            server = HttpServer.create(new InetSocketAddress(port), 0);

            // Use a thread pool for handling requests
            server.setExecutor(Executors.newFixedThreadPool(
                    Runtime.getRuntime().availableProcessors() * 2
            ));

            // Static resource handler (must be first to catch all requests)
            server.createContext("/", new StaticResourceHandler());

            // API endpoints
            server.createContext("/api/metrics", new MetricsHandler());
            server.createContext("/api/metrics/list", new MetricListHandler());
            server.createContext("/api/hotspots", new EntityHotspotsHandler());
            server.createContext("/api/lagspikes", new LagSpikesHandler());
            server.createContext("/api/config", new ConfigHandler());

            // Start the server
            server.start();
            LOGGER.info("Web server started on port {}", port);
        } catch (Exception e) {
            LOGGER.error("Failed to start web server", e);
            throw new RuntimeException("Unable to start web server", e);
        }
    }

    /**
     * Stops the web server
     */
    public void stop() {
        if (server != null) {
            server.stop(0);
            LOGGER.info("Web server stopped");
        }
    }

    /**
     * Sends an error response with JSON format
     *
     * This method is a centralized way to send error responses across all handlers
     *
     * @param exchange The HttpExchange object representing the current request
     * @param statusCode The HTTP status code to send
     * @param message The error message to include in the response
     */
    private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
        // Create a simple error response object
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("status", statusCode);
        errorResponse.put("error", message);

        // Convert to JSON
        String jsonErrorResponse = gson.toJson(errorResponse);

        // Convert to bytes
        byte[] responseBytes = jsonErrorResponse.getBytes(StandardCharsets.UTF_8);

        // Set response headers
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        // Write response body
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Handler for serving static resources
     */
    private class StaticResourceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Default to index.html for root path
            if ("/".equals(path) || path.isEmpty()) {
                path = "/index.html";
            }

            // Special case for the problematic path
            if (path.contains("/js/modules/utils/chart-utils.js")) {
                path = "/js/utils/chart-utils.js";
            }

            // Remove leading slash for resource lookup
            String resourcePath = path.startsWith("/") ? path.substring(1) : path;

            try {
                // Try multiple potential resource locations
                URL resourceUrl = findResource(resourcePath);

                if (resourceUrl != null) {
                    // Read resource content
                    byte[] response = readResourceBytes(resourceUrl);

                    // Determine MIME type
                    String mimeType = determineMimeType(path);

                    // Send response
                    exchange.getResponseHeaders().set("Content-Type", mimeType);
                    exchange.sendResponseHeaders(200, response.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(response);
                    }
                } else {
                    // Resource not found
                    sendErrorResponse(exchange, 404, "Not Found: " + path);
                }
            } catch (IOException e) {
                LOGGER.error("Error serving static resource: {}", path, e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }

        /**
         * Flexible resource finder that checks multiple potential locations
         */
        private URL findResource(String resourcePath) {
            ClassLoader classLoader = getClassLoader();

            // Potential resource locations to check
            String[] searchLocations = {
                    "static/" + resourcePath,  // Default location
                    "static/js/" + resourcePath,
                    "static/js/modules/" + resourcePath,
                    "static/js/utils/" + resourcePath,
                    "static/js/modules/utils/" + resourcePath
            };

            for (String location : searchLocations) {
                URL resourceUrl = classLoader.getResource(location);
                if (resourceUrl != null) {
                    LOGGER.debug("Found resource at: {}", location);
                    return resourceUrl;
                }
            }

            LOGGER.warn("Resource not found: {}", resourcePath);
            return null;
        }

        /**
         * Reads resource bytes from a URL
         */
        private byte[] readResourceBytes(URL resourceUrl) throws IOException {
            try (var inputStream = resourceUrl.openStream()) {
                return inputStream.readAllBytes();
            }
        }

        /**
         * Gets the appropriate ClassLoader
         */
        private ClassLoader getClassLoader() {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            return classLoader != null ? classLoader : WebServer.class.getClassLoader();
        }

        /**
         * Determines MIME type based on file extension
         */
        private String determineMimeType(String path) {
            // Default to plain text if no match found
            String defaultMimeType = "text/plain";

            // Find the last dot in the path
            int lastDotIndex = path.lastIndexOf('.');
            if (lastDotIndex == -1) {
                return defaultMimeType;
            }

            // Extract file extension
            String extension = path.substring(lastDotIndex);

            // Return MIME type or default
            return MIME_TYPES.getOrDefault(extension.toLowerCase(), defaultMimeType);
        }
    }

    /**
     * Handles metrics retrieval requests
     */
    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Parse query parameters
                String query = exchange.getRequestURI().getQuery();
                Map<String, String> params = parseQueryParameters(query);

                // Check if specific metrics were requested
                String metricPrefix = params.getOrDefault("prefix", null);
                String metricName = params.getOrDefault("metric", null);

                // Get metrics based on request
                Map<String, List<MetricPoint>> metrics;
                if (metricName != null) {
                    // Return a single specific metric
                    metrics = new HashMap<>();
                    List<MetricPoint> points = metricsManager.getMetric(metricName);
                    if (!points.isEmpty()) {
                        metrics.put(metricName, points);
                    }
                } else if (metricPrefix != null) {
                    // Return metrics with a specific prefix
                    metrics = metricsManager.getMetricsByPrefix(metricPrefix);
                } else {
                    // Return all metrics
                    metrics = metricsManager.getMetrics();
                }

                // Convert to JSON
                String jsonResponse = gson.toJson(metrics);

                // Send the response
                sendJsonResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                LOGGER.error("Error handling metrics request", e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Handles entity hotspots retrieval requests
     */
    private class EntityHotspotsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Fetch entity hotspots from entity metrics collector
                Map<String, Object> hotspots = new HashMap<>();

                // You might want to implement a method in EntityMetricsCollector to get hotspots
                // For now, we'll return an empty response
                String jsonResponse = gson.toJson(hotspots);

                // Send the response
                sendJsonResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                LOGGER.error("Error handling entity hotspots request", e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Handles metric list retrieval requests
     */
    private class MetricListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Get all available metric names
                Set<String> metricNames = metricsManager.getAvailableMetrics();

                // Create response object
                Map<String, Object> response = new HashMap<>();
                response.put("metrics", metricNames);
                response.put("total", metricNames.size());

                // Convert to JSON
                String jsonResponse = gson.toJson(response);

                // Send the response
                sendJsonResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                LOGGER.error("Error handling metric list request", e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Handles lag spikes retrieval requests
     */
    private class LagSpikesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Get lag spikes from system metrics collector
                List<SystemMetricsCollector.LagSpike> lagSpikes =
                        metricsManager.getSystemCollector().getRecentLagSpikes();

                // Create response object
                Map<String, Object> response = new HashMap<>();
                response.put("lagSpikes", lagSpikes);
                response.put("count", lagSpikes.size());

                // Convert to JSON
                String jsonResponse = gson.toJson(response);

                // Send the response
                sendJsonResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                LOGGER.error("Error handling lag spikes request", e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Handles configuration retrieval requests
     */
    private class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendErrorResponse(exchange, 405, "Method Not Allowed");
                return;
            }

            try {
                // Create a basic configuration response
                Map<String, Object> config = new HashMap<>();
                config.put("metricsCollectionInterval",
                        Nevformance.getInstance().getConfigManager().getMetricCollectionIntervalMs());
                config.put("metricsHistorySize",
                        Nevformance.getInstance().getConfigManager().getMetricsHistorySize());

                // Convert to JSON
                String jsonResponse = gson.toJson(config);

                // Send the response
                sendJsonResponse(exchange, 200, jsonResponse);
            } catch (Exception e) {
                LOGGER.error("Error handling config request", e);
                sendErrorResponse(exchange, 500, "Internal Server Error");
            }
        }
    }

    /**
     * Helper method to send JSON responses
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String jsonResponse) throws IOException {
        byte[] responseBytes = jsonResponse.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }

    /**
     * Helper method to parse query parameters
     */
    private Map<String, String> parseQueryParameters(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                int idx = pair.indexOf("=");
                if (idx > 0) {
                    String key = pair.substring(0, idx);
                    String value = pair.substring(idx + 1);
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    // ... (rest of the previous implementation remains the same)
}