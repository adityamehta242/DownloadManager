package com.example.downloadmanager.core;

import com.example.downloadmanager.downloader.Downloader;
import com.example.downloadmanager.scheduler.DownloadQueue;
import com.example.downloadmanager.utils.LoggerUtil;
import com.example.downloadmanager.utils.URLValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central controller managing all downloads in the application.
 */
public class DownloadManager {

    // Map to store active downloads by their IDs
    private final Map<String, Downloader> activeDownloads;

    // Queue for scheduling downloads
    private DownloadQueue downloadQueue;

    // URL validator utility
    private URLValidator urlValidator;

    /**
     * Constructor initializes collections and dependencies
     */
    public DownloadManager() {
        this.activeDownloads = new HashMap<>();
        this.downloadQueue = new DownloadQueue();
        this.urlValidator = new URLValidator();

        LoggerUtil.logInfo("Download Manager initialized");
    }

    /**
     * Adds a new download to the system
     *
     * @param url The URL to download
     * @return The generated ID for this download
     */
    public String addDownload(String url) {
        // Validate URL
        if (!urlValidator.isValidURL(url)) {
            LoggerUtil.logError("Invalid URL: " + url, null);
            throw new IllegalArgumentException("Invalid URL provided");
        }

        // Generate a unique ID for this download
        String downloadId = UUID.randomUUID().toString();

        // Create a new downloader for this URL
        Downloader downloader = new Downloader(url);

        // Add to active downloads map
        activeDownloads.put(downloadId, downloader);

        LoggerUtil.logInfo("Added new download: " + url + " with ID: " + downloadId);

        return downloadId;
    }

    /**
     * Starts a download with the given ID
     *
     * @param id The download ID
     */
    public void startDownload(String id) {
        Downloader downloader = activeDownloads.get(id);
        if (downloader == null) {
            LoggerUtil.logError("Download not found: " + id, null);
            throw new IllegalArgumentException("Download ID not found");
        }

        // Enqueue the download task
        downloadQueue.enqueue(downloader);
        LoggerUtil.logInfo("Download enqueued: " + id);

        // Start the download if possible
        downloadQueue.runNext();
    }

    /**
     * Pauses an active download
     *
     * @param id The download ID
     */
    public void pauseDownload(String id) {
        Downloader downloader = activeDownloads.get(id);
        if (downloader == null) {
            LoggerUtil.logError("Download not found: " + id, null);
            throw new IllegalArgumentException("Download ID not found");
        }

        downloader.pause();
        LoggerUtil.logInfo("Download paused: " + id);
    }

    /**
     * Resumes a paused download
     *
     * @param id The download ID
     */
    public void resumeDownload(String id) {
        Downloader downloader = activeDownloads.get(id);
        if (downloader == null) {
            LoggerUtil.logError("Download not found: " + id, null);
            throw new IllegalArgumentException("Download ID not found");
        }

        downloader.resume();
        LoggerUtil.logInfo("Download resumed: " + id);
    }

    /**
     * Cancels and removes a download
     *
     * @param id The download ID
     */
    public void cancelDownload(String id) {
        Downloader downloader = activeDownloads.get(id);
        if (downloader == null) {
            LoggerUtil.logError("Download not found: " + id, null);
            throw new IllegalArgumentException("Download ID not found");
        }

        // Remove from queue if needed
        downloadQueue.remove(id);

        // Cancel the download
        downloader.cancel();

        // Remove from active downloads
        activeDownloads.remove(id);

        LoggerUtil.logInfo("Download cancelled: " + id);
    }

    /**
     * Gets the status of a download
     *
     * @param id The download ID
     * @return The current status
     */
    public DownloadStatus getStatus(String id) {
        Downloader downloader = activeDownloads.get(id);
        if (downloader == null) {
            LoggerUtil.logError("Download not found: " + id, null);
            throw new IllegalArgumentException("Download ID not found");
        }

        return downloader.getStatus();
    }

    /**
     * Simple status class to track download progress and info
     */
    public static class DownloadStatus {
        private final String url;
        private final long downloadedBytes;
        private final long totalBytes;
        private final String state; // QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR

        public DownloadStatus(String url, long downloadedBytes, long totalBytes, String state) {
            this.url = url;
            this.downloadedBytes = downloadedBytes;
            this.totalBytes = totalBytes;
            this.state = state;
        }

        // Getters
        public String getUrl() { return url; }
        public long getDownloadedBytes() { return downloadedBytes; }
        public long getTotalBytes() { return totalBytes; }
        public String getState() { return state; }
        public float getProgress() {
            if (totalBytes <= 0) return 0;
            return (float) downloadedBytes / totalBytes;
        }

        @Override
        public String toString() {
            return String.format("Download: %s, Progress: %.1f%%, State: %s",
                    url, getProgress() * 100, state);
        }
    }
}