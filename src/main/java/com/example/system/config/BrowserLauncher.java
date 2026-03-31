package com.example.system.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens the default browser to the application URL once the server is fully ready.
 * Uses OS-native commands so it works even when AWT Desktop is unavailable (headless JRE).
 */
@Component
@ConditionalOnProperty(name = "system.launch-browser", havingValue = "true", matchIfMissing = true)
public class BrowserLauncher {

    @Value("${server.port:8080}")
    private int serverPort;

    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        LocalBrowserOpener.openLocalDashboard(serverPort);
    }
}
