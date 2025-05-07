package com.example.downloadmanager.downloader;


public class DownloadChunk {
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
