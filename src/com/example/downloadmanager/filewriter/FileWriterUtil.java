package com.example.downloadmanager.filewriter;

import com.example.downloadmanager.utils.LoggerUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility for writing file chunks to disk
 */
public class FileWriterUtil {

    // Cache of open file handles to avoid repeatedly opening/closing files
    private final Map<String, RandomAccessFile> fileHandles;

    // Buffer for pending writes to improve performance
    private final Map<String, WriteBuffer> writeBuffers;

    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB buffer

    /**
     * Constructor initializes collections
     */
    public FileWriterUtil() {
        this.fileHandles = new HashMap<>();
        this.writeBuffers = new HashMap<>();
    }

    /**
     * Writes a chunk of data to the specified file at the given offset
     *
     * @param data The data to write
     * @param offset The position in the file to write at
     * @param filePath The path to the file
     * @throws IOException If an I/O error occurs
     */
    public synchronized void writeChunk(byte[] data, long offset, String filePath) throws IOException {
        try {
            // Get or create file handle
            RandomAccessFile file = getFileHandle(filePath);

            // Get or create write buffer
            WriteBuffer buffer = writeBuffers.computeIfAbsent(filePath, k -> new WriteBuffer(BUFFER_SIZE));

            // If this write would make the buffer non-contiguous, flush first
            if (buffer.size() > 0 && buffer.getEndOffset() != offset) {
                flushBuffer(file, buffer);
            }

            // If buffer is empty, set its start offset
            if (buffer.size() == 0) {
                buffer.setStartOffset(offset);
            }

            // Add data to buffer
            buffer.add(data);

            // If buffer is full or nearly full, flush it
            if (buffer.size() >= buffer.getCapacity() * 0.9) {
                flushBuffer(file, buffer);
            }

        } catch (IOException e) {
            LoggerUtil.logError("Error writing chunk to file: " + filePath, e);
            throw e;
        }
    }

    /**
     * Flushes all buffered data to disk
     *
     * @throws IOException If an I/O error occurs
     */
    public synchronized void flush() throws IOException {
        for (Map.Entry<String, RandomAccessFile> entry : fileHandles.entrySet()) {
            String filePath = entry.getKey();
            RandomAccessFile file = entry.getValue();
            WriteBuffer buffer = writeBuffers.get(filePath);

            if (buffer != null && buffer.size() > 0) {
                flushBuffer(file, buffer);
            }
        }
    }

    /**
     * Flushes a specific buffer to its file
     *
     * @param file The file to write to
     * @param buffer The buffer to flush
     * @throws IOException If an I/O error occurs
     */
    private void flushBuffer(RandomAccessFile file, WriteBuffer buffer) throws IOException {
        FileLock lock = null;
        try {
            // Seek to the buffer's start position
            file.seek(buffer.getStartOffset());

            // Try to acquire an exclusive lock on this region
            lock = file.getChannel().lock(buffer.getStartOffset(), buffer.size(), false);

            // Write the data
            file.write(buffer.getData(), 0, buffer.size());

            // Clear the buffer for reuse
            buffer.clear();

        } finally {
            // Release the lock
            if (lock != null) {
                lock.release();
            }
        }
    }

    /**
     * Gets or opens a file handle
     *
     * @param filePath The path to the file
     * @return The RandomAccessFile handle
     * @throws IOException If an I/O error occurs
     */
    private RandomAccessFile getFileHandle(String filePath) throws IOException {
        RandomAccessFile file = fileHandles.get(filePath);

        if (file == null) {
            // Ensure parent directories exist
            File f = new File(filePath);
            f.getParentFile().mkdirs();

            // Open file with read/write access
            file = new RandomAccessFile(filePath, "rw");
            fileHandles.put(filePath, file);
        }

        return file;
    }

    /**
     * Closes all open file handles
     */
    public synchronized void closeAll() {
        // First flush any pending writes
        try {
            flush();
        } catch (IOException e) {
            LoggerUtil.logError("Error flushing buffers", e);
        }

        // Close all file handles
        for (RandomAccessFile file : fileHandles.values()) {
            try {
                file.close();
            } catch (IOException e) {
                LoggerUtil.logError("Error closing file", e);
            }
        }

        // Clear collections
        fileHandles.clear();
        writeBuffers.clear();
    }

    /**
     * Checks file integrity (simple existence check for now)
     *
     * @param filePath The path to check
     * @return true if file exists and has data
     */
    public boolean checkIntegrity(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.length() > 0;
    }

    /**
     * Calculates MD5 hash of a file (can be used for verification)
     *
     * @param filePath The path to the file
     * @return The MD5 hash as a hex string
     */
    public String calculateMD5(String filePath) {
        try {
            Path path = Paths.get(filePath);
            byte[] data = Files.readAllBytes(path);

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(data);

            // Convert to hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }

            return sb.toString();

        } catch (Exception e) {
            LoggerUtil.logError("Error calculating MD5: " + filePath, e);
            return null;
        }
    }

    /**
     * Inner class for buffering writes
     */
    private static class WriteBuffer {
        private byte[] buffer;
        private int position;
        private long startOffset;

        public WriteBuffer(int capacity) {
            this.buffer = new byte[capacity];
            this.position = 0;
            this.startOffset = 0;
        }

        public void add(byte[] data) {
            // Ensure capacity
            if (position + data.length > buffer.length) {
                // Expand buffer if needed
                byte[] newBuffer = new byte[Math.max(buffer.length * 2, position + data.length)];
                System.arraycopy(buffer, 0, newBuffer, 0, position);
                buffer = newBuffer;
            }

            // Copy data to buffer
            System.arraycopy(data, 0, buffer, position, data.length);
            position += data.length;
        }

        public byte[] getData() {
            return buffer;
        }

        public int size() {
            return position;
        }

        public int getCapacity() {
            return buffer.length;
        }

        public void clear() {
            position = 0;
        }

        public void setStartOffset(long offset) {
            this.startOffset = offset;
        }

        public long getStartOffset() {
            return startOffset;
        }

        public long getEndOffset() {
            return startOffset + position;
        }
    }
}