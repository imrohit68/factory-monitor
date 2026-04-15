package com.example.system.config;

import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Stops the Spring context and exits the JVM (e.g. admin-initiated shutdown).
 */
@Service
public class ApplicationShutdownService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationShutdownService.class);

    private final ConfigurableApplicationContext context;
    private final AtomicBoolean shutdownScheduled = new AtomicBoolean(false);

    /** Gives the browser time to receive the shutdown-ack response before the process exits. */
    private final long shutdownDelayMs;

    public ApplicationShutdownService(
            ConfigurableApplicationContext context,
            @Value("${system.shutdown.delay-ms:2000}") long shutdownDelayMs) {
        this.context = context;
        this.shutdownDelayMs = Math.max(200L, shutdownDelayMs);
    }

    /**
     * Returns immediately; shutdown runs on a delay so the HTTP response can complete. Duplicate
     * requests are ignored so double-submit does not start parallel exit threads.
     */
    public void shutdownGracefully() {
        if (!shutdownScheduled.compareAndSet(false, true)) {
            log.info("Shutdown already scheduled; ignoring duplicate request.");
            return;
        }
        Thread t = new Thread(this::exitAfterDelay, "pcs-shutdown");
        t.setDaemon(false);
        t.start();
    }

    private void exitAfterDelay() {
        try {
            Thread.sleep(shutdownDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int code = SpringApplication.exit(context, () -> 0);
        exitJvm(code);
    }

    /** Separated from {@link System#exit} so tests can stub shutdown without mocking {@link System}. */
    void exitJvm(int code) {
        System.exit(code);
    }
}
