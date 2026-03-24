package com.example.demo.service;

/**
 * Tracks input level: relay and events follow the bit — high → open, low → close (no two-press ack).
 */
final class ChannelFsm {

    private boolean prevLevel;
    private boolean initialized;
    private boolean inputActive;

    void onSample(boolean level, Runnable onOpen, Runnable onClose) {
        if (!initialized) {
            initialized = true;
            prevLevel = level;
            inputActive = level;
            if (level) {
                onOpen.run();
            }
            return;
        }
        if (level == prevLevel) {
            return;
        }
        prevLevel = level;
        inputActive = level;
        if (level) {
            onOpen.run();
        } else {
            onClose.run();
        }
    }

    boolean isInputActive() {
        return inputActive;
    }
}
