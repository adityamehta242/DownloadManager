package com.example.downloadmanager.persistence;

import com.example.downloadmanager.connection.ConnectionHandler;
import com.example.downloadmanager.utils.LoggerUtil;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistence of download states to enable resume functionality
 */
public class DownloadStateManager {

    private static final String STATE_DIR = "download_states";
    private static final String STATE_FILE_EXTENSION = ".state";
    private final Map<String, DownloadState> activeDownloads;
    private final File stateDirectory;

    /**
     * Constructor initializes state directory and active downloads map
     */
    public DownloadStateManager() {
        // Initialize in-memory store of active downloads
        this.activeDownloads = new ConcurrentHashMap<>();

        // Create state directory if it doesn't exist
        this.stateDirectory = new File(STATE_DIR);
        if (!stateDirectory.exists() && !stateDirectory.mkdirs()) {
            LoggerUtil.logError("Failed to create state directory: " + stateDirectory.getAbsolutePath(), null);
        }
    }

    /**
     * Saves the current state of a download
     *
     * @param downloadId Unique identifier for the download
     * @param url The download URL
     * @param filePath Local file path
     * @param totalBytes Total file size in bytes
     * @param bytesDownloaded Number of bytes downloaded so far
     * @param chunks List of download chunks with their progress
     * @param state Current download state (QUEUED, DOWNLOADING, PAUSED, etc.)
     * @return true if state was saved successfully
     */
    public boolean saveDownloadState(String downloadId, String url, String filePath,
                                     long totalBytes, long bytesDownloaded,
                                     List<DownloadChunk> chunks, String state) {
        try {
            DownloadState downloadState = new DownloadState(
                    downloadId, url, filePath, totalBytes, bytesDownloaded, chunks, state
            );

            // Save to in-memory map
            activeDownloads.put(downloadId, downloadState);

            // Save to disk
            File stateFile = getStateFile(downloadId);
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(stateFile))) {
                oos.writeObject(downloadState);
            }

            return true;
        } catch (IOException e) {
            LoggerUtil.logError("Failed to save download state: " + downloadId, e);
            return false;
        }
    }

    /**
     * Retrieves a download state by ID
     *
     * @param downloadId Unique identifier for the download
     * @return DownloadState object or null if not found
     */
    public DownloadState getDownloadState(String downloadId) {
        // Try to get from in-memory cache first
        DownloadState state = activeDownloads.get(downloadId);

        // If not in memory, try to load from disk
        if (state == null) {
            File stateFile = getStateFile(downloadId);
            if (stateFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFile))) {
                    state = (DownloadState) ois.readObject();
                    // Update in-memory cache
                    activeDownloads.put(downloadId, state);
                } catch (IOException | ClassNotFoundException e) {
                    LoggerUtil.logError("Failed to load download state: " + downloadId, e);
                }
            }
        }

        return state;
    }

    /**
     * Updates the download progress for a given download
     *
     * @param downloadId Unique identifier for the download
     * @param bytesDownloaded Updated number of bytes downloaded
     * @param updatedChunks Updated list of chunks
     * @return true if update was successful
     */
    public boolean updateProgress(String downloadId, long bytesDownloaded, List<DownloadChunk> updatedChunks) {
        DownloadState state = getDownloadState(downloadId);
        if (state != null) {
            state.setBytesDownloaded(bytesDownloaded);
            state.setChunks(updatedChunks);
            return saveDownloadState(
                    state.getDownloadId(),
                    state.getUrl(),
                    state.getFilePath(),
                    state.getTotalBytes(),
                    bytesDownloaded,
                    updatedChunks,
                    state.getState()
            );
        }
        return false;
    }

    /**
     * Updates the state of a download (QUEUED, DOWNLOADING, PAUSED, etc.)
     *
     * @param downloadId Unique identifier for the download
     * @param newState New state value
     * @return true if update was successful
     */
    public boolean updateState(String downloadId, String newState) {
        DownloadState state = getDownloadState(downloadId);
        if (state != null) {
            state.setState(newState);
            return saveDownloadState(
                    state.getDownloadId(),
                    state.getUrl(),
                    state.getFilePath(),
                    state.getTotalBytes(),
                    state.getBytesDownloaded(),
                    state.getChunks(),
                    newState
            );
        }
        return false;
    }

    /**
     * Removes a download state
     *
     * @param downloadId Unique identifier for the download
     * @return true if removal was successful
     */
    public boolean removeDownloadState(String downloadId) {
        // Remove from in-memory map
        activeDownloads.remove(downloadId);

        // Remove from disk
        File stateFile = getStateFile(downloadId);
        if (stateFile.exists()) {
            return stateFile.delete();
        }
        return true;
    }

    /**
     * Lists all saved downloads
     *
     * @return List of download states
     */
    public List<DownloadState> getAllDownloads() {
        List<DownloadState> allDownloads = new ArrayList<>();

        // Add downloads from disk that might not be in memory
        File[] stateFiles = stateDirectory.listFiles((dir, name) -> name.endsWith(STATE_FILE_EXTENSION));
        if (stateFiles != null) {
            for (File file : stateFiles) {
                String downloadId = file.getName().replace(STATE_FILE_EXTENSION, "");
                DownloadState state = getDownloadState(downloadId);
                if (state != null && !allDownloads.contains(state)) {
                    allDownloads.add(state);
                }
            }
        }

        return allDownloads;
    }

    /**
     * Gets all downloads with a specific state
     *
     * @param state The state to filter by
     * @return List of download states matching the filter
     */
    public List<DownloadState> getDownloadsByState(String state) {
        return getAllDownloads().stream()
                .filter(download -> download.getState().equals(state))
                .toList();
    }

    /**
     * Helper method to get the state file for a download ID
     *
     * @param downloadId The download ID
     * @return File object for the state file
     */
    private File getStateFile(String downloadId) {
        return new File(stateDirectory, downloadId + STATE_FILE_EXTENSION);
    }

    /**
     * Handles cleanup tasks when the application shuts down
     */
    public void shutdown() {
        // Ensure all in-memory states are persisted
        for (DownloadState state : activeDownloads.values()) {
            try {
                File stateFile = getStateFile(state.getDownloadId());
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(stateFile))) {
                    oos.writeObject(state);
                }
            } catch (IOException e) {
                LoggerUtil.logError("Failed to save download state during shutdown: " + state.getDownloadId(), e);
            }
        }
    }

    /**
     * Checks if temporary files for incomplete downloads exist and creates state objects for them
     * Used during application startup to recover interrupted downloads
     *
     * @param tempDir Directory containing temporary download files
     */
    public void recoverInterruptedDownloads(String tempDir) {
        try {
            Path directory = Paths.get(tempDir);
            if (!Files.exists(directory)) {
                return;
            }

            Files.list(directory)
                    .filter(path -> path.toString().endsWith(".part"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        // Extract original info from the part filename (implementation depends on your naming convention)
                        String originalUrl = extractUrlFromPartFile(fileName);
                        if (originalUrl != null) {
                            String downloadId = generateDownloadId(originalUrl);

                            // Check if we already have a state for this download
                            if (getDownloadState(downloadId) == null) {
                                // Create a new state for this interrupted download
                                try {
                                    long fileSize = Files.size(path);
                                    saveDownloadState(
                                            downloadId,
                                            originalUrl,
                                            path.toString().replace(".part", ""),
                                            -1, // Unknown total size, will be determined on resume
                                            fileSize,
                                            new ArrayList<>(), // Empty chunks, will be recalculated on resume
                                            "INTERRUPTED"
                                    );
                                    LoggerUtil.logInfo("Recovered interrupted download: " + originalUrl);
                                } catch (IOException e) {
                                    LoggerUtil.logError("Failed to recover interrupted download: " + path, e);
                                }
                            }
                        }
                    });
        } catch (IOException e) {
            LoggerUtil.logError("Failed to scan for interrupted downloads", e);
        }
    }

    /**
     * Extracts the original URL from a partial download filename
     * Implementation depends on how you name your partial files
     */
    private String extractUrlFromPartFile(String partFileName) {
        // This is a placeholder implementation
        // In a real implementation, you would decode the URL from your naming convention
        try {
            // Example: If you store metadata in a separate file alongside the .part file
            String metadataFile = partFileName.replace(".part", ".meta");
            Path path = Paths.get(stateDirectory.getPath(), metadataFile);
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                if (!lines.isEmpty()) {
                    return lines.get(0); // First line contains URL
                }
            }
        } catch (IOException e) {
            LoggerUtil.logError("Failed to extract URL from part file: " + partFileName, e);
        }
        return null;
    }

    /**
     * Generates a unique download ID from a URL
     */
    private String generateDownloadId(String url) {
        return UUID.nameUUIDFromBytes(url.getBytes()).toString();
    }

    /**
     * Class representing the state of a download task
     */
    public static class DownloadState implements Serializable {
        private static final long serialVersionUID = 1L;

        private final String downloadId;
        private final String url;
        private final String filePath;
        private long totalBytes;
        private long bytesDownloaded;
        private List<DownloadChunk> chunks;
        private String state;
        private final long createdAt;
        private long lastUpdatedAt;

        public DownloadState(String downloadId, String url, String filePath,
                             long totalBytes, long bytesDownloaded,
                             List<DownloadChunk> chunks, String state) {
            this.downloadId = downloadId;
            this.url = url;
            this.filePath = filePath;
            this.totalBytes = totalBytes;
            this.bytesDownloaded = bytesDownloaded;
            this.chunks = new ArrayList<>(chunks);
            this.state = state;
            this.createdAt = System.currentTimeMillis();
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        // Getters and setters
        public String getDownloadId() { return downloadId; }
        public String getUrl() { return url; }
        public String getFilePath() { return filePath; }
        public long getTotalBytes() { return totalBytes; }
        public long getBytesDownloaded() { return bytesDownloaded; }
        public List<DownloadChunk> getChunks() { return chunks; }
        public String getState() { return state; }
        public long getCreatedAt() { return createdAt; }
        public long getLastUpdatedAt() { return lastUpdatedAt; }

        public void setTotalBytes(long totalBytes) {
            this.totalBytes = totalBytes;
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        public void setBytesDownloaded(long bytesDownloaded) {
            this.bytesDownloaded = bytesDownloaded;
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        public void setChunks(List<DownloadChunk> chunks) {
            this.chunks = new ArrayList<>(chunks);
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        public void setState(String state) {
            this.state = state;
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DownloadState that = (DownloadState) o;
            return downloadId.equals(that.downloadId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(downloadId);
        }

        @Override
        public String toString() {
            return "DownloadState{" +
                    "downloadId='" + downloadId + '\'' +
                    ", url='" + url + '\'' +
                    ", state='" + state + '\'' +
                    ", progress=" + (totalBytes > 0 ? (bytesDownloaded * 100 / totalBytes) : 0) + "%" +
                    '}';
        }
    }

    /**
     * Class representing a chunk of a download
     */
    public static class DownloadChunk implements Serializable {
        private static final long serialVersionUID = 1L;

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

        // Getters and setters
        public long getStartByte() { return startByte; }
        public long getEndByte() { return endByte; }
        public long getCurrentPosition() { return currentPosition; }
        public boolean isCompleted() { return completed; }

        public void setCurrentPosition(long currentPosition) {
            this.currentPosition = currentPosition;
            this.completed = (currentPosition >= endByte);
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
            if (completed) {
                this.currentPosition = endByte;
            }
        }

        public long getRemainingBytes() {
            return endByte - currentPosition + 1;
        }

        public long getTotalBytes() {
            return endByte - startByte + 1;
        }

        public double getProgressPercentage() {
            if (getTotalBytes() <= 0) return 0;
            return ((double) (currentPosition - startByte) / getTotalBytes()) * 100;
        }

        @Override
        public String toString() {
            return "DownloadChunk{" +
                    "startByte=" + startByte +
                    ", endByte=" + endByte +
                    ", currentPosition=" + currentPosition +
                    ", completed=" + completed +
                    ", progress=" + String.format("%.2f", getProgressPercentage()) + "%" +
                    '}';
        }
    }
}