package com.example.system.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Paths;

/**
 * General application settings (not serial/Modbus-specific).
 */
@ConfigurationProperties(prefix = "system")
@Getter
@Setter
public class AppProperties {

    /** Max calendar-day span for CSV export (inclusive start–end). */
    private int logRetentionDays = 30;

    /**
     * Dashboard alert sound: wait this many minutes after a clip finishes before playing it again (while the
     * input stays active).
     */
    private int dashboardAlertRepeatIntervalMinutes = 15;

    /**
     * Dashboard alert sound: maximum number of those repeats after the initial play, per channel, while the
     * alert stays active (0 = initial play only).
     */
    private int dashboardAlertMaxRepeats = 4;

    /**
     * If true, only one JVM may run per {@link #dataDir}; a second start opens the browser and exits.
     */
    private boolean singleInstance = false;

    /**
     * If true, the dashboard sends heartbeats; when they stop (tab closed), the process exits after
     * {@link #getBrowserCloseGraceSeconds()}.
     */
    @Getter(AccessLevel.NONE)
    private boolean exitOnBrowserClose = false;

    /** No heartbeat for this long (after at least one heartbeat) triggers shutdown when exit-on-browser-close is on. */
    @Getter(AccessLevel.NONE)
    private int browserCloseGraceSeconds = 20;

    /** Interval for dashboard heartbeat POSTs (seconds), exposed to the UI. */
    @Getter(AccessLevel.NONE)
    private int browserHeartbeatIntervalSeconds = 5;

    /**
     * Root folder for persisted files: SQLite database and (unless overridden) uploaded alert audio.
     * Back up this directory to recover both DB and audio; DB references URLs like {@code /audio/uploads/...}
     * which map to files under {@link #getAudioUploadDir()}.
     */
    private String dataDir = Paths.get(System.getProperty("user.home"), "factory-monitor-data").toString();

    /**
     * Optional override for uploaded workstation audio (served under /audio/uploads/). If blank,
     * defaults to {@code {dataDir}/alert-audio} so audio stays with the database for recovery.
     */
    @Getter(AccessLevel.NONE)
    private String audioUploadDir = "";

    public String getAudioUploadDir() {
        if (audioUploadDir != null && !audioUploadDir.isBlank()) {
            return audioUploadDir;
        }
        return Paths.get(dataDir, "alert-audio").toString();
    }

    public boolean isExitOnBrowserClose() {
        return exitOnBrowserClose;
    }

    public int getBrowserCloseGraceSeconds() {
        return browserCloseGraceSeconds;
    }

    public int getBrowserHeartbeatIntervalSeconds() {
        return browserHeartbeatIntervalSeconds;
    }
}
