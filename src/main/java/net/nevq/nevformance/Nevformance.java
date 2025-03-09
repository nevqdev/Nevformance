package net.nevq.nevformance;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.nevq.nevformance.metrics.MetricsManager;
import net.nevq.nevformance.web.WebServer;
import net.nevq.nevformance.config.ConfigManager;

public class Nevformance implements ModInitializer {
	// This logger is used to write text to the console and the log file.
	public static final Logger LOGGER = LoggerFactory.getLogger("nevformance");

	private MetricsManager metricsManager;
	private WebServer webServer;
	private ConfigManager configManager;
	private static Nevformance instance;

	@Override
	public void onInitialize() {
		instance = this;
		LOGGER.info("Initializing Nevformance Mod");

		// Load configuration
		configManager = new ConfigManager();
		configManager.loadConfig();

		// Initialize metrics system
		metricsManager = new MetricsManager();

		// Register server lifecycle events
		registerServerEvents();

		LOGGER.info("Nevformance Mod initialized");
	}

	private void registerServerEvents() {
		// Start the web server and metrics collection when the server starts
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LOGGER.info("Minecraft server started, initializing analytics components");
			metricsManager.startCollection(server);

			// Start the web server
			int port = configManager.getWebServerPort();
			webServer = new WebServer(port, metricsManager);
			webServer.start();

			LOGGER.info("Analytics web interface available at http://localhost:" + port);
		});

		// Stop everything when the server stops
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LOGGER.info("Minecraft server stopping, shutting down analytics components");
			if (webServer != null) {
				webServer.stop();
			}
			metricsManager.stopCollection();
		});
	}

	public static Nevformance getInstance() {
		return instance;
	}

	public MetricsManager getMetricsManager() {
		return metricsManager;
	}

	public ConfigManager getConfigManager() {
		return configManager;
	}
}