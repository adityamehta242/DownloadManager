package com.example.downloadmanager.utils;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Utility class for validating URLs and checking their properties
 */
public class URLValidator {

    /**
     * Validates if a given string is a properly formatted URL
     *
     * @param url The URL string to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidURL(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            LoggerUtil.logError("Malformed URL: " + url, e);
            return false;
        }
    }

    /**
     * Checks if the server supports byte range requests
     *
     * @param connection The HTTP connection to check
     * @return true if range requests are supported
     */
    public boolean supportsRange(HttpURLConnection connection) {
        try {
            // Check if "Accept-Ranges" header is present and not "none"
            String acceptRanges = connection.getHeaderField("Accept-Ranges");
            if (acceptRanges != null && !acceptRanges.equals("none")) {
                return true;
            }

            // Alternative check: try a HEAD request with Range header
            connection.setRequestMethod("HEAD");
            connection.setRequestProperty("Range", "bytes=0-0");
            int responseCode = connection.getResponseCode();

            // 206 Partial Content indicates range support
            return responseCode == 206;

        } catch (Exception e) {
            LoggerUtil.logError("Error checking range support: " + connection.getURL(), e);
            return false;
        }
    }

    /**
     * Gets information about a URL without downloading the full content
     *
     * @param url The URL to check
     * @return true if the URL exists and is accessible
     */
    public boolean canConnect(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();

            // Check if response code indicates success (2xx) or redirection (3xx)
            return (responseCode >= 200 && responseCode < 400);

        } catch (Exception e) {
            LoggerUtil.logError("Error connecting to URL: " + url, e);
            return false;
        }
    }
}