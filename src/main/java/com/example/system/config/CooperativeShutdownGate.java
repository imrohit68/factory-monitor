package com.example.system.config;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Signals that the JVM or Spring context is shutting down so scheduled Modbus work can stop before
 * the serial session is closed ({@code @PreDestroy} / lifecycle), avoiding torn I/O and scheduler
 * timeouts.
 */
@Component
public class CooperativeShutdownGate implements SmartLifecycle {

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private volatile boolean running;

    public boolean isShuttingDown() {
        return shuttingDown.get();
    }

    /** Used when shutdown is initiated out-of-band (e.g. admin HTTP) before {@link #stop()}. */
    public void markShuttingDown() {
        shuttingDown.set(true);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        markShuttingDown();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Stops first on context close so {@link #isShuttingDown()} is true before lower-phase beans
     * (including the task scheduler) wind down.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
