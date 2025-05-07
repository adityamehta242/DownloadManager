package com.example.downloadmanager.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Utility class for application-wide logging
 */
public class LoggerUtil {

    private static final Logger LOGGER = Logger.getLogger("DownloadManager");
    private static boolean initialized = false;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Initializes the logger with appropriate handlers and formatters
     */
    public static void init() {
        if (initialized) {
            return;
        }

        try {
            // Remove default handlers
            for (var handler : LOGGER.getHandlers()) {
                LOGGER.removeHandler(handler);
            }

            // Create console handler
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.ALL);

            // Create file handler for persistent logs
            FileHandler fileHandler = new FileHandler("download-manager.log", true);
            fileHandler.setLevel(Level.ALL);
            fileHandler.setFormatter(new SimpleFormatter());

            // Add handlers to logger
            LOGGER.addHandler(consoleHandler);
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);

            // Turn off annoying parent handler
            LOGGER.setUseParentHandlers(false);

            initialized = true;
            logInfo("Logger initialized");
        } catch (Exception e) {
            System.err.println("Failed to initialize logger: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Logs an informational message
     *
     * @param message The message to log
     */
    public static void logInfo(String message) {
        ensureInitialized();
        String timestamp = LocalDateTime.now().format(formatter);
        LOGGER.info(timestamp + " - " + message);
    }

    /**
     * Logs an error message along with exception details
     *
     * @param message The error message
     * @param e The exception (can be null)
     */
    public static void logError(String message, Exception e) {
        ensureInitialized();
        String timestamp = LocalDateTime.now().format(formatter);

        if (e != null) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            LOGGER.severe(timestamp + " - " + message + "\n" + sw.toString());
        } else {
            LOGGER.severe(timestamp + " - " + message);
        }
    }

    /**
     * Logs a debug message
     *
     * @param message The debug message
     */
    public static void logDebug(String message) {
        ensureInitialized();
        String timestamp = LocalDateTime.now().format(formatter);
        LOGGER.fine(timestamp + " - " + message);
    }

    /**
     * Makes sure logger is initialized before use
     */
    private static void ensureInitialized() {
        if (!initialized) {
            init();
        }
    }
}