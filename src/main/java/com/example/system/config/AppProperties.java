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
     * {@code event_log} rows with {@code event_time} strictly older than this many calendar days are removed
     * by a daily job. {@code 0} disables automatic purge.
     */
    private int eventLogPurgeRetentionDays = 30;

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
     * Root folder for persisted files: SQLite database and (unless overridden) uploaded alert audio.
     * Back up this directory to recover both DB and audio; DB references URLs like {@code /audio/uploads/...}
     * which map to files under {@link #getAudioUploadDir()}.
     */
    private String dataDir = Paths.get(System.getProperty("user.home"), "production-calling-system-data").toString();

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
}
