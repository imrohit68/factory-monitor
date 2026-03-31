package com.example.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Opens the default browser to the application URL once the server is fully ready.
 * Uses OS-native commands so it works even when AWT Desktop is unavailable (headless JRE).
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Value("${server.port:8080}")
    private int serverPort;

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        String url = "http://localhost:" + serverPort;
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c", "start", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.inheritIO();
            pb.start();
        } catch (IOException | SecurityException e) {
            log.warn("Could not open browser at {}: {}", url, e.getMessage());
        }
    }
}
