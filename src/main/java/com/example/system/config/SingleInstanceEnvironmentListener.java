package com.example.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * When {@code system.single-instance=true}, acquires an exclusive lock under the data directory before the
 * context starts. If another process already holds the lock, opens the dashboard in the browser and exits.
 */
public class SingleInstanceEnvironmentListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceEnvironmentListener.class);

    /** Kept open for the lifetime of the JVM so the OS holds the file lock. */
    @SuppressWarnings("resource")
    private static RandomAccessFile lockHolder;

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        Environment env = event.getEnvironment();
        if (!Boolean.TRUE.equals(env.getProperty("system.single-instance", Boolean.class))) {
            return;
        }
        String dataDir = resolveDataDir(env);
        Path lockPath = Paths.get(dataDir, "factory-monitor.lock");
        try {
            Files.createDirectories(lockPath.getParent());
            RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
            FileChannel channel = raf.getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                raf.close();
                int port = env.getProperty("server.port", Integer.class, 8080);
                log.info("Another Factory Monitor instance is already running; opening browser on port {}.", port);
                LocalBrowserOpener.openLocalDashboard(port);
                System.exit(0);
                return;
            }
            lockHolder = raf;
        } catch (Exception e) {
            throw new IllegalStateException("Could not acquire single-instance lock at " + lockPath, e);
        }
    }

    static String resolveDataDir(Environment env) {
        String d = env.getProperty("system.data-dir");
        if (d == null || d.isBlank()) {
            return Paths.get(System.getProperty("user.home"), "factory-monitor-data").toString();
        }
        return d.trim();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
