package com.example.downloadmanager.downloader;

import com.example.downloadmanager.connection.ConnectionHandler;
import com.example.downloadmanager.core.DownloadManager.DownloadStatus;
import com.example.downloadmanager.filewriter.FileWriterUtil;
import com.example.downloadmanager.utils.LoggerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages a single download job with multi-threading support
 */
public class Downloader {

    private final String url;
    private String filePath;
    private final AtomicLong bytesDownloaded;
    private long totalBytes;
    private String state; // QUEUED, DOWNLOADING, PAUSED, COMPLETED, ERROR
    private final AtomicBoolean isPaused;
    private final AtomicBoolean isCancelled;

    private List<DownloadChunk> chunks;
    private List<Thread> downloadThreads;

    private final ConnectionHandler connectionHandler;
    private final FileWriterUtil fileWriter;

    /**
     * Constructor initializes a new download
     *
     * @param url The URL to download
     */
    public Downloader(String url) {
        this.url = url;
        this.bytesDownloaded = new AtomicLong(0);
        this.totalBytes = -1; // Unknown until we connect
        this.state = "QUEUED";
        this.isPaused = new AtomicBoolean(false);
        this.isCancelled = new AtomicBoolean(false);
        this.chunks = new ArrayList<>();
        this.downloadThreads = new ArrayList<>();

        this.connectionHandler = new ConnectionHandler();
        this.fileWriter = new FileWriterUtil();

        // Extract filename from URL
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        if (fileName.isEmpty()) {
            fileName = "download_" + System.currentTimeMillis();
        }

        this.filePath = "downloads" + File.separator + fileName;

        // Create downloads directory if it doesn't exist
        new File("downloads").mkdirs();
    }

    /**
     * Starts the downloading process
     */
    public void start() {
        try {
            if (!"QUEUED".equals(state) && !"PAUSED".equals(state)) {
                return;
            }

            state = "DOWNLOADING";
            isPaused.set(false);
            isCancelled.set(false);

            // First, get file information including size
            if (totalBytes <= 0) {
                ConnectionHandler.FileInfo fileInfo = connectionHandler.getFileInfo(url);
                totalBytes = fileInfo.getContentLength();
                LoggerUtil.logInfo("Starting download: " + url + ", Size: " + totalBytes + " bytes");
            }

            // Calculate number of chunks based on file size
            int numThreads = calculateOptimalThreads();

            // If we have chunks from a previous pause, use those
            if (chunks.isEmpty()) {
                chunks = splitIntoChunks(totalBytes, numThreads);
            }

            // Start a thread for each chunk
            downloadThreads.clear();
            for (DownloadChunk chunk : chunks) {
                if (chunk.isCompleted()) {
                    continue; // Skip completed chunks
                }

                Thread thread = new Thread(() -> {
                    try {
                        downloadChunk(chunk);
                    } catch (Exception e) {
                        LoggerUtil.logError("Error downloading chunk: " + chunk, e);
                    }
                });

                downloadThreads.add(thread);
                thread.start();
            }

            // Start a monitor thread to check for completion
            new Thread(() -> {
                try {
                    for (Thread t : downloadThreads) {
                        t.join();
                    }

                    // If not cancelled and all threads completed
                    if (!isCancelled.get() && bytesDownloaded.get() >= totalBytes) {
                        state = "COMPLETED";
                        LoggerUtil.logInfo("Download completed: " + url);

                        // Optional: Verify file integrity
                        if (fileWriter.checkIntegrity(filePath)) {
                            LoggerUtil.logInfo("File integrity verified: " + filePath);
                        } else {
                            LoggerUtil.logError("File integrity check failed: " + filePath, null);
                        }
                    }
                } catch (InterruptedException e) {
                    LoggerUtil.logError("Download monitor interrupted", e);
                }
            }).start();

        } catch (Exception e) {
            state = "ERROR";
            LoggerUtil.logError("Error starting download: " + url, e);
        }
    }

    /**
     * Downloads a specific chunk of the file
     *
     * @param chunk The chunk to download
     */
    private void downloadChunk(DownloadChunk chunk) {
        long current = chunk.getCurrentPosition();
        final long end = chunk.getEndByte();

        try {
            while (current < end && !isCancelled.get()) {
                // Check for pause
                while (isPaused.get() && !isCancelled.get()) {
                    Thread.sleep(500);
                }

                if (isCancelled.get()) {
                    return;
                }

                // Download a part of the chunk
                long bytesToDownload = Math.min(1024 * 1024, end - current); // 1MB at a time
                byte[] data = connectionHandler.downloadChunk(url, current, current + bytesToDownload - 1);

                // Write the data to disk
                fileWriter.writeChunk(data, current, filePath);

                // Update progress
                current += data.length;
                chunk.setCurrentPosition(current);
                bytesDownloaded.addAndGet(data.length);
            }

            if (current >= end) {
                chunk.setCompleted(true);
            }

        } catch (Exception e) {
            LoggerUtil.logError("Error in chunk download: " + chunk, e);
        }
    }

    /**
     * Pauses the download
     */
    public void pause() {
        if ("DOWNLOADING".equals(state)) {
            isPaused.set(true);
            state = "PAUSED";
            LoggerUtil.logInfo("Download paused: " + url);
        }
    }

    /**
     * Resumes the download
     */
    public void resume() {
        if ("PAUSED".equals(state)) {
            isPaused.set(false);
            start(); // Restart where we left off
        }
    }

    /**
     * Cancels the download
     */
    public void cancel() {
        isCancelled.set(true);
        isPaused.set(false); // Wake up paused threads so they can terminate
        state = "CANCELLED";

        // Interrupt all threads
        for (Thread thread : downloadThreads) {
            thread.interrupt();
        }

        LoggerUtil.logInfo("Download cancelled: " + url);
    }

    /**
     * Splits the file into chunks for parallel downloading
     *
     * @param fileSize Total size of the file
     * @param threads Number of threads/chunks to use
     * @return List of chunks
     */
    private List<DownloadChunk> splitIntoChunks(long fileSize, int threads) {
        List<DownloadChunk> result = new ArrayList<>();

        // If file size is unknown or single thread is preferred
        if (fileSize <= 0 || threads <= 1) {
            result.add(new DownloadChunk(0, fileSize > 0 ? fileSize - 1 : Long.MAX_VALUE));
            return result;
        }

        long chunkSize = fileSize / threads;
        for (int i = 0; i < threads; i++) {
            long startByte = i * chunkSize;
            long endByte = (i == threads - 1) ? fileSize - 1 : (i + 1) * chunkSize - 1;
            result.add(new DownloadChunk(startByte, endByte));
        }

        return result;
    }

    /**
     * Calculates optimal thread count based on file size
     *
     * @return Number of threads to use
     */
    private int calculateOptimalThreads() {
        if (totalBytes <= 0) {
            return 1; // Unknown size, use single thread
        }

        // For files smaller than 10MB, use 2 threads
        if (totalBytes < 10 * 1024 * 1024) {
            return 2;
        }

        // For files smaller than 100MB, use 4 threads
        if (totalBytes < 100 * 1024 * 1024) {
            return 4;
        }

        // For larger files, use 8 threads
        return 8;
    }

    /**
     * Gets the current status of this download
     *
     * @return Status information
     */
    public DownloadStatus getStatus() {
        return new DownloadStatus(url, bytesDownloaded.get(), totalBytes, state);
    }

    /**
     * Gets the URL being downloaded
     *
     * @return The URL
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the file path where download is saved
     *
     * @return The file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Gets the completed percentage
     *
     * @return Percentage as float (0-1)
     */
    public float getProgress() {
        if (totalBytes <= 0) return 0;
        return (float) bytesDownloaded.get() / totalBytes;
    }

    /**
     * Inner class representing a chunk of the download
     */
    public static class DownloadChunk {
        private final long startByte;
        private final long endByte;
        private long currentPosition;
        private boolean completed;

        public DownloadChunk(long startByte, long endByte) {
            this.startByte = startByte;
            this.endByte = endByte;
            this.currentPosition = startByte;
            this.completed = false;
        }

        public long getStartByte() { return startByte; }
        public long getEndByte() { return endByte; }
        public long getCurrentPosition() { return currentPosition; }
        public void setCurrentPosition(long position) { this.currentPosition = position; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }

        @Override
        public String toString() {
            return String.format("Chunk[%d-%d], Current: %d, Completed: %s",
                    startByte, endByte, currentPosition, completed);
        }
    }
}