package com.example.system.config;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Stops the Spring context and exits the JVM so desktop / installer runs can be ended from the admin UI.
 */
@Service
public class ApplicationShutdownService {

    private final ConfigurableApplicationContext context;

    public ApplicationShutdownService(ConfigurableApplicationContext context) {
        this.context = context;
    }

    /** Returns immediately; shutdown runs on a short delay so the HTTP response can complete. */
    public void shutdownGracefully() {
        Thread t = new Thread(this::exitAfterDelay, "factory-monitor-shutdown");
        t.setDaemon(false);
        t.start();
    }

    private void exitAfterDelay() {
        try {
            Thread.sleep(800L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = SpringApplication.exit(context, () -> 0);
        System.exit(code);
    }
}
