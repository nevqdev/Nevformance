package net.nevq.nevformance.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("nevformance");
    private static final String CONFIG_FILE = "nevformance.json";

    // Default configuration values
    private int webServerPort = 8080;
    private int metricCollectionIntervalMs = 1000; // 1 second by default
    private boolean collectTileEntityMetrics = true;
    private boolean collectEntityMetrics = true;
    private boolean collectChunkMetrics = true;
    private int metricsHistorySize = 3600; // Store 1 hour of data by default

    // Custom metrics to collect (class name -> enabled)
    private Map<String, Boolean> customMetrics = new HashMap<>();

    public void loadConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE);

        // Create the config file if it doesn't exist
        if (!Files.exists(configFile)) {
            saveConfig(); // This will create the file with default values
            return;
        }

        // Load the config file
        try (Reader reader = Files.newBufferedReader(configFile)) {
            Gson gson = new Gson();
            ConfigData configData = gson.fromJson(reader, ConfigData.class);

            // Apply the loaded settings
            if (configData != null) {
                this.webServerPort = configData.webServerPort;
                this.metricCollectionIntervalMs = configData.metricCollectionIntervalMs;
                this.collectTileEntityMetrics = configData.collectTileEntityMetrics;
                this.collectEntityMetrics = configData.collectEntityMetrics;
                this.collectChunkMetrics = configData.collectChunkMetrics;
                this.metricsHistorySize = configData.metricsHistorySize;

                if (configData.customMetrics != null) {
                    this.customMetrics = configData.customMetrics;
                }
            }

            LOGGER.info("Configuration loaded successfully");
        } catch (Exception e) {
            LOGGER.error("Failed to load configuration", e);
            // Use defaults if loading fails
        }
    }

    public void saveConfig() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE);

        try {
            Files.createDirectories(configDir);

            try (Writer writer = Files.newBufferedWriter(configFile)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                ConfigData configData = new ConfigData();

                // Transfer current settings to the data object
                configData.webServerPort = this.webServerPort;
                configData.metricCollectionIntervalMs = this.metricCollectionIntervalMs;
                configData.collectTileEntityMetrics = this.collectTileEntityMetrics;
                configData.collectEntityMetrics = this.collectEntityMetrics;
                configData.collectChunkMetrics = this.collectChunkMetrics;
                configData.metricsHistorySize = this.metricsHistorySize;
                configData.customMetrics = this.customMetrics;

                gson.toJson(configData, writer);
                LOGGER.info("Configuration saved successfully");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
        }
    }

    // Getter methods
    public int getWebServerPort() {
        return webServerPort;
    }

    public int getMetricCollectionIntervalMs() {
        return metricCollectionIntervalMs;
    }

    public boolean isCollectTileEntityMetrics() {
        return collectTileEntityMetrics;
    }

    public boolean isCollectEntityMetrics() {
        return collectEntityMetrics;
    }

    public boolean isCollectChunkMetrics() {
        return collectChunkMetrics;
    }

    public int getMetricsHistorySize() {
        return metricsHistorySize;
    }

    public Map<String, Boolean> getCustomMetrics() {
        return customMetrics;
    }

    // Setter methods with save option
    public void setWebServerPort(int webServerPort) {
        this.webServerPort = webServerPort;
        saveConfig();
    }

    public void setMetricCollectionIntervalMs(int metricCollectionIntervalMs) {
        this.metricCollectionIntervalMs = metricCollectionIntervalMs;
        saveConfig();
    }

    // Inner class for JSON serialization/deserialization
    private static class ConfigData {
        int webServerPort = 8080;
        int metricCollectionIntervalMs = 1000;
        boolean collectTileEntityMetrics = true;
        boolean collectEntityMetrics = true;
        boolean collectChunkMetrics = true;
        int metricsHistorySize = 3600;
        Map<String, Boolean> customMetrics = new HashMap<>();
    }
}