package net.nevq.nevformance.util;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * Utility methods for metrics collection and processing
 */
public class MetricsUtil {

    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * Gets a safe dimension key for use in metric names
     * @param world The server world
     * @return A safe string identifier for the dimension
     */
    public static String getDimensionKey(ServerWorld world) {
        Identifier dimensionId = world.getRegistryKey().getValue();
        String namespace = dimensionId.getNamespace();
        String path = dimensionId.getPath();

        // Replace ':' with '.' for metric name compatibility
        return sanitizeMetricName(namespace + "." + path);
    }

    /**
     * Sanitizes a string for use in metric names
     * @param input The input string
     * @return A sanitized string safe for use in metric names
     */
    public static String sanitizeMetricName(String input) {
        // Replace any invalid characters with underscores
        return INVALID_CHARS.matcher(input).replaceAll("_");
    }

    /**
     * Formats a timestamp for display
     * @param timestamp Timestamp in milliseconds
     * @return Formatted date/time string
     */
    public static String formatTimestamp(long timestamp) {
        return DATE_FORMAT.format(new Date(timestamp));
    }

    /**
     * Gets the dimension type name in a human-readable format
     * @param world The server world
     * @return Human-readable dimension name
     */
    public static String getDimensionName(ServerWorld world) {
        Identifier dimensionId = world.getRegistryKey().getValue();

        if (dimensionId.equals(World.OVERWORLD.getValue())) {
            return "Overworld";
        } else if (dimensionId.equals(World.NETHER.getValue())) {
            return "Nether";
        } else if (dimensionId.equals(World.END.getValue())) {
            return "End";
        } else {
            // Custom dimension - use the path part of the identifier
            return capitalizeWords(dimensionId.getPath().replace("_", " "));
        }
    }

    /**
     * Capitalizes the first letter of each word in a string
     * @param str The input string
     * @return String with capitalized words
     */
    public static String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        StringBuilder result = new StringBuilder();
        String[] words = str.split("\\s");

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    /**
     * Formats a double value for display
     * @param value The double value
     * @param decimals Number of decimal places
     * @return Formatted string
     */
    public static String formatDouble(double value, int decimals) {
        return String.format("%." + decimals + "f", value);
    }

    /**
     * Formats file size in bytes to a human-readable format
     * @param bytes Size in bytes
     * @return Human-readable size string
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.2f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}