package com.example.downloadmanager.connection;

import com.example.downloadmanager.utils.LoggerUtil;
import com.example.downloadmanager.utils.RetryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Supplier;

/**
 * Handles HTTP/FTP connections with range headers for chunk downloading
 */
public class ConnectionHandler {

    private static final int CONNECT_TIMEOUT = 15000; // 15 seconds
    private static final int READ_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_RETRIES = 3;
    private static final int BUFFER_SIZE = 8192; // 8KB buffer

    private final RetryUtil retryUtil;

    /**
     * Constructor initializes helper utilities
     */
    public ConnectionHandler() {
        this.retryUtil = new RetryUtil();
    }

    /**
     * Downloads a chunk of data from the given URL range
     *
     * @param url The URL to download from
     * @param start Starting byte position
     * @param end Ending byte position
     * @return The downloaded data
     * @throws IOException If an I/O error occurs
     */
    public byte[] downloadChunk(String url, long start, long end) throws IOException {
        Supplier<byte[]> downloadAction = () -> {
            try {
                HttpURLConnection connection = createConnection(url);

                // Set the range header
                connection.setRequestProperty("Range", "bytes=" + start + "-" + end);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_PARTIAL && responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Unexpected response code: " + responseCode);
                }

                // Calculate expected size
                long expectedSize = end - start + 1;

                // Read the data
                try (InputStream in = connection.getInputStream()) {
                    return readAllBytes(in, expectedSize);
                }
            } catch (IOException e) {
                LoggerUtil.logError("Download chunk error: " + url + " [" + start + "-" + end + "]", e);
                throw new RuntimeException(e);
            }
        };

        // Use retry logic for network operations
        byte[] result = retryUtil.retry(downloadAction, MAX_RETRIES);
        if (result == null) {
            throw new IOException("Failed to download chunk after " + MAX_RETRIES + " attempts");
        }

        return result;
    }

    /**
     * Gets file information from the URL without downloading the content
     *
     * @param url The URL to check
     * @return FileInfo containing metadata
     * @throws IOException If an I/O error occurs
     */
    public FileInfo getFileInfo(String url) throws IOException {
        Supplier<FileInfo> infoAction = () -> {
            try {
                HttpURLConnection connection = createConnection(url);
                connection.setRequestMethod("HEAD");

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Unexpected response code: " + responseCode);
                }

                // Get content length
                long contentLength = connection.getContentLengthLong();

                // Get content type
                String contentType = connection.getContentType();

                // Check if the server supports range requests
                boolean supportsRanges = false;
                String acceptRanges = connection.getHeaderField("Accept-Ranges");
                if (acceptRanges != null && !acceptRanges.equals("none")) {
                    supportsRanges = true;
                }

                // Get filename if available
                String fileName = getFileNameFromHeader(connection);
                if (fileName == null) {
                    fileName = getFileNameFromUrl(url);
                }

                return new FileInfo(contentLength, contentType, supportsRanges, fileName);
            } catch (IOException e) {
                LoggerUtil.logError("Failed to get file info: " + url, e);
                throw new RuntimeException(e);
            }
        };

        // Use retry logic
        FileInfo info = retryUtil.retry(infoAction, MAX_RETRIES);
        if (info == null) {
            throw new IOException("Failed to get file info after " + MAX_RETRIES + " attempts");
        }

        return info;
    }

    /**
     * Creates and configures an HttpURLConnection
     *
     * @param urlString The URL to connect to
     * @return Configured HttpURLConnection
     * @throws IOException If an I/O error occurs
     */
    private HttpURLConnection createConnection(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Configure connection parameters
        connection.setConnectTimeout(CONNECT_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setUseCaches(false);

        // Set common headers
        connection.setRequestProperty("User-Agent", "DownloadManager/1.0");

        return connection;
    }

    /**
     * Reads all bytes from an input stream with expected size
     *
     * @param in The input stream to read from
     * @param expectedSize Expected number of bytes to read
     * @return Byte array containing all read bytes
     * @throws IOException If an I/O error occurs
     */
    private byte[] readAllBytes(InputStream in, long expectedSize) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) Math.min(expectedSize, Integer.MAX_VALUE));
        byte[] data = new byte[BUFFER_SIZE];
        int bytesRead;
        long totalBytesRead = 0;

        while ((bytesRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
            totalBytesRead += bytesRead;

            // Check if we've read enough data
            if (totalBytesRead >= expectedSize) {
                break;
            }
        }

        if (totalBytesRead < expectedSize) {
            LoggerUtil.logError(
                    "Warning: Expected " + expectedSize + " bytes but received " + totalBytesRead,
                    null
            );
        }

        return buffer.toByteArray();
    }

    /**
     * Extracts filename from Content-Disposition header
     *
     * @param connection The HTTP connection
     * @return The filename or null if not found
     */
    private String getFileNameFromHeader(HttpURLConnection connection) {
        String contentDisposition = connection.getHeaderField("Content-Disposition");
        if (contentDisposition != null) {
            // Parse filename from Content-Disposition header
            // Example: attachment; filename="example.zip"
            int filenameIndex = contentDisposition.indexOf("filename=");
            if (filenameIndex != -1) {
                String filename = contentDisposition.substring(filenameIndex + 9);
                // Remove quotes if present
                if (filename.startsWith("\"") && filename.endsWith("\"")) {
                    filename = filename.substring(1, filename.length() - 1);
                }
                return filename;
            }
        }
        return null;
    }

    /**
     * Extracts filename from URL
     *
     * @param urlString The URL
     * @return The filename
     */
    private String getFileNameFromUrl(String urlString) {
        int lastSlashIndex = urlString.lastIndexOf('/');
        if (lastSlashIndex != -1 && lastSlashIndex < urlString.length() - 1) {
            String filename = urlString.substring(lastSlashIndex + 1);

            // Remove query parameters if present
            int queryIndex = filename.indexOf('?');
            if (queryIndex != -1) {
                filename = filename.substring(0, queryIndex);
            }

            return filename.isEmpty() ? "download" : filename;
        }
        return "download";
    }

    /**
     * FileInfo class to hold metadata about a downloadable file
     */
    public static class FileInfo {
        private final long contentLength;
        private final String contentType;
        private final boolean supportsRanges;
        private final String fileName;

        public FileInfo(long contentLength, String contentType, boolean supportsRanges, String fileName) {
            this.contentLength = contentLength;
            this.contentType = contentType;
            this.supportsRanges = supportsRanges;
            this.fileName = fileName;
        }

        public long getContentLength() {
            return contentLength;
        }

        public String getContentType() {
            return contentType;
        }

        public boolean isSupportsRanges() {
            return supportsRanges;
        }

        public String getFileName() {
            return fileName;
        }

        @Override
        public String toString() {
            return "FileInfo{" +
                    "contentLength=" + contentLength +
                    ", contentType='" + contentType + '\'' +
                    ", supportsRanges=" + supportsRanges +
                    ", fileName='" + fileName + '\'' +
                    '}';
        }
    }
}