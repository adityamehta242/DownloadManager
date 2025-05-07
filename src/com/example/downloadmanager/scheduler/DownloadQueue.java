package com.example.downloadmanager.scheduler;

import com.example.downloadmanager.downloader.Downloader;
import com.example.downloadmanager.utils.LoggerUtil;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages the queue of downloads and controls concurrency
 */
public class DownloadQueue {

    // Queue of downloads waiting to start
    private final Queue<Downloader> pendingDownloads;

    // Currently active downloads
    private final Map<String, Downloader> activeDownloads;

    // Maximum concurrent downloads
    private int maxConcurrentDownloads;

    // Executor for periodic queue processing
    private final ScheduledExecutorService scheduler;

    /**
     * Constructor initializes collections and default settings
     */
    public DownloadQueue() {
        this.pendingDownloads = new LinkedList<>();
        this.activeDownloads = new ConcurrentHashMap<>();
        this.maxConcurrentDownloads = 3; // Default value

        // Create a scheduler to periodically check the queue
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::processQueue, 5, 5, TimeUnit.SECONDS);

        LoggerUtil.logInfo("Download queue initialized with max concurrent downloads: " + maxConcurrentDownloads);
    }

    /**
     * Adds a download to the queue
     *
     * @param downloader The downloader to enqueue
     */
    public synchronized void enqueue(Downloader downloader) {
        pendingDownloads.add(downloader);
        LoggerUtil.logInfo("Download added to queue: " + downloader.getUrl());
    }

    /**
     * Gets the next download from the queue
     *
     * @return The next downloader or null if queue is empty
     */
    public synchronized Downloader dequeue() {
        return pendingDownloads.poll();
    }

    /**
     * Removes a download from the queue or active downloads
     *
     * @param id The download ID
     */
    public synchronized void remove(String id) {
        // Remove from active downloads if present
        activeDownloads.remove(id);

        // Remove from pending queue if present
        pendingDownloads.removeIf(downloader ->
                downloader.getUrl().equals(id)); // Using URL as ID for simplicity
    }

    /**
     * Processes the queue and starts downloads if slots are available
     */
    private synchronized void processQueue() {
        try {
            // Check if we can start more downloads
            int availableSlots = maxConcurrentDownloads - activeDownloads.size();

            for (int i = 0; i < availableSlots && !pendingDownloads.isEmpty(); i++) {
                Downloader downloader = dequeue();
                if (downloader != null) {
                    startDownload(downloader);
                }
            }
        } catch (Exception e) {
            LoggerUtil.logError("Error processing download queue", e);
        }
    }

    /**
     * Starts the next download in the queue if slots are available
     */
    public synchronized void runNext() {
        try {
            // Check if we can start more downloads
            if (activeDownloads.size() < maxConcurrentDownloads && !pendingDownloads.isEmpty()) {
                Downloader downloader = dequeue();
                if (downloader != null) {
                    startDownload(downloader);
                }
            }
        } catch (Exception e) {
            LoggerUtil.logError("Error starting next download", e);
        }
    }

    /**
     * Starts a download and adds it to active downloads
     *
     * @param downloader The downloader to start
     */
    private void startDownload(Downloader downloader) {
        // Add to active downloads
        activeDownloads.put(downloader.getUrl(), downloader);

        // Start in a separate thread
        new Thread(() -> {
            try {
                downloader.start();

                // Remove from active downloads when complete
                // This is simplified; in a real implementation we'd monitor for completion
                activeDownloads.remove(downloader.getUrl());

                // Process queue after a download finishes
                runNext();

            } catch (Exception e) {
                LoggerUtil.logError("Error in download thread: " + downloader.getUrl(), e);
                activeDownloads.remove(downloader.getUrl());
            }
        }).start();

        LoggerUtil.logInfo("Started download: " + downloader.getUrl());
    }

    /**
     * Sets the maximum number of concurrent downloads
     *
     * @param max The new maximum
     */
    public synchronized void setMaxConcurrent(int max) {
        if (max < 1) {
            throw new IllegalArgumentException("Max concurrent downloads must be at least 1");
        }

        this.maxConcurrentDownloads = max;
        LoggerUtil.logInfo("Max concurrent downloads set to: " + max);

        // Process queue in case we can start more downloads now
        processQueue();
    }

    /**
     * Gets the current number of downloads in the queue
     *
     * @return Queue size
     */
    public synchronized int getQueueSize() {
        return pendingDownloads.size();
    }

    /**
     * Gets the current number of active downloads
     *
     * @return Active download count
     */
    public synchronized int getActiveCount() {
        return activeDownloads.size();
    }

    /**
     * Cleans up resources when done
     */
    public void shutdown() {
        scheduler.shutdown();
        LoggerUtil.logInfo("Download queue shutdown");
    }
}