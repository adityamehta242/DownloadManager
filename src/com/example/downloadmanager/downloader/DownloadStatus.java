package com.example.downloadmanager.downloader;


public class DownloadStatus {
    private final String url;
    private final long downloadedBytes;
    private final long totalBytes;
    private final String state;

    public DownloadStatus(String url, long downloadedBytes, long totalBytes, String state) {
        this.url = url;
        this.downloadedBytes = downloadedBytes;
        this.totalBytes = totalBytes;
        this.state = state;
    }

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
