package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "andon")
public class AndonAppProperties {

    /** Max calendar-day span for CSV export (inclusive start–end). */
    private int logRetentionDays = 30;

    /** Directory for uploaded workstation alert audio (served under /audio/uploads/). */
    private String audioUploadDir = "./data/andon-audio";

    /**
     * When true and the workstation table is empty, insert the default 16 columns (48 inputs / 48 relays)
     * matching the original layout: E bits 0–15, L bits 16–31, Q bits 32–47; relays 1–32 on first slave,
     * 33–48 on second.
     */
    private boolean seedDefaultWorkstations = true;

    /** Modbus slave for relays 1–32 (Engineer + Leader columns in the default grid). */
    private int defaultRelaySlaveFirst = 2;

    /** Modbus slave for relays 33–48 (Quality row in the default grid). */
    private int defaultRelaySlaveSecond = 3;

    public int getLogRetentionDays() {
        return logRetentionDays;
    }

    public void setLogRetentionDays(int logRetentionDays) {
        this.logRetentionDays = logRetentionDays;
    }

    public String getAudioUploadDir() {
        return audioUploadDir;
    }

    public void setAudioUploadDir(String audioUploadDir) {
        this.audioUploadDir = audioUploadDir;
    }

    public boolean isSeedDefaultWorkstations() {
        return seedDefaultWorkstations;
    }

    public void setSeedDefaultWorkstations(boolean seedDefaultWorkstations) {
        this.seedDefaultWorkstations = seedDefaultWorkstations;
    }

    public int getDefaultRelaySlaveFirst() {
        return defaultRelaySlaveFirst;
    }

    public void setDefaultRelaySlaveFirst(int defaultRelaySlaveFirst) {
        this.defaultRelaySlaveFirst = defaultRelaySlaveFirst;
    }

    public int getDefaultRelaySlaveSecond() {
        return defaultRelaySlaveSecond;
    }

    public void setDefaultRelaySlaveSecond(int defaultRelaySlaveSecond) {
        this.defaultRelaySlaveSecond = defaultRelaySlaveSecond;
    }

}
