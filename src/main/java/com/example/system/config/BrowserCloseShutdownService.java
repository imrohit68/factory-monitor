package com.example.system.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * When {@code system.exit-on-browser-close=true}, the dashboard POSTs a periodic heartbeat with a per-process
 * token. If heartbeats stop (e.g. tab closed), the app shuts down after a grace period. Refreshes usually
 * resume heartbeats within the grace window.
 */
@Service
public class BrowserCloseShutdownService {

    private final AppProperties appProperties;
    private final ApplicationShutdownService applicationShutdownService;

    private final String sessionToken = newSessionToken();

    /** 0 until the first valid heartbeat (dashboard never opened). */
    private volatile long lastHeartbeatMs;

    private volatile boolean staleShutdownTriggered;

    public BrowserCloseShutdownService(AppProperties appProperties, ApplicationShutdownService applicationShutdownService) {
        this.appProperties = appProperties;
        this.applicationShutdownService = applicationShutdownService;
    }

    private static String newSessionToken() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    public boolean isExitOnBrowserClose() {
        return appProperties.isExitOnBrowserClose();
    }

    public String getSessionToken() {
        return sessionToken;
    }

    /** @return true if token matched and heartbeat was recorded */
    public synchronized boolean recordHeartbeatIfValid(String token) {
        if (!appProperties.isExitOnBrowserClose()) {
            return false;
        }
        if (token == null || !sessionToken.equals(token)) {
            return false;
        }
        lastHeartbeatMs = System.currentTimeMillis();
        return true;
    }

    @Scheduled(fixedDelay = 5000)
    public void checkStaleHeartbeat() {
        if (!appProperties.isExitOnBrowserClose() || staleShutdownTriggered) {
            return;
        }
        long last = lastHeartbeatMs;
        if (last <= 0) {
            return;
        }
        long graceMs = appProperties.getBrowserCloseGraceSeconds() * 1000L;
        if (System.currentTimeMillis() - last > graceMs) {
            staleShutdownTriggered = true;
            applicationShutdownService.shutdownGracefully();
        }
    }
}
