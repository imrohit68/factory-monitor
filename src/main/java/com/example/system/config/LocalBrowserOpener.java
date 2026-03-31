package com.example.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Opens the default browser to the local dashboard URL (used on startup and when a second instance detects the first).
 */
public final class LocalBrowserOpener {

    private static final Logger log = LoggerFactory.getLogger(LocalBrowserOpener.class);

    private LocalBrowserOpener() {}

    public static void openLocalDashboard(int port) {
        String url = "http://127.0.0.1:" + port;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", "", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectInput(ProcessBuilder.Redirect.DISCARD);
            pb.start();
        } catch (IOException | SecurityException e) {
            log.warn("Could not open browser at {}: {}", url, e.getMessage());
        }
    }
}

