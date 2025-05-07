package com.example.downloadmanager;

import com.example.downloadmanager.core.DownloadManager;
import com.example.downloadmanager.utils.LoggerUtil;

/**
 * Entry point for the Download Manager application.
 */
public class Main {

    public static void main(String[] args) {
        // Initialize logger
        LoggerUtil.init();
        LoggerUtil.logInfo("Starting Download Manager application");

        // Create and initialize the download manager
        DownloadManager downloadManager = new DownloadManager();

        // For now, we'll just handle a simple console-based interaction
        // Later, this will be replaced with a proper UI
        if (args.length > 0) {
            // If URL is provided as command line argument
            String url = args[0];
            downloadManager.addDownload(url);
            downloadManager.startDownload(url);
        } else {
            System.out.println("Usage: java -jar downloadmanager.jar [URL]");
            System.out.println("No URL provided. Starting in interactive mode.");
            // Later we will initialize UI here
        }
    }
}