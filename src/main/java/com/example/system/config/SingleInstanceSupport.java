package com.example.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Runs before {@link org.springframework.boot.SpringApplication#run} so a second desktop launch never starts
 * a partial Spring context or fights for the HTTP port. If the lock is held or the port is already listening,
 * notifies the running instance (desktop activation or browser) and terminates this JVM immediately.
 */
public final class SingleInstanceSupport {

    private static final Logger log = LoggerFactory.getLogger(SingleInstanceSupport.class);

    @SuppressWarnings("resource")
    private static RandomAccessFile lockHolder;

    private static volatile boolean singleInstanceEnabled;
    private static volatile boolean desktopMode;
    private static volatile boolean desktopFullscreen = true;
    /** When false, the JavaFX window close button does not exit the app (use Admin → Shut down). */
    private static volatile boolean desktopAllowWindowClose = true;
    private static volatile OperationMode operationModeOverride;
    private static volatile int configuredPort = 8080;
    private static volatile int activationPort;

    private SingleInstanceSupport() {}

    public static boolean isSingleInstanceEnabled() {
        return singleInstanceEnabled;
    }

    public static boolean isDesktopMode() {
        return desktopMode;
    }

    /**
     * When {@link #isDesktopMode()} is true, whether the JavaFX stage should use exclusive fullscreen (no window
     * frame; typically covers the taskbar until the OS shows it on edge hover).
     */
    public static boolean isDesktopFullscreen() {
        return desktopFullscreen;
    }

    /**
     * When {@link #isDesktopMode()} is true, whether closing the JavaFX stage should stop Spring and exit the JVM.
     * When false, the close request is ignored (operators exit via Admin → Shut down application).
     */
    public static boolean isDesktopAllowWindowClose() {
        return desktopAllowWindowClose;
    }

    public static OperationMode getOperationModeOverride() {
        return operationModeOverride;
    }

    public static void setOperationMode(OperationMode operationMode) {
        operationModeOverride = operationMode;
    }

    public static int getConfiguredPort() {
        return configuredPort;
    }

    /** Loopback TCP port for {@link com.example.system.desktop.InstanceActivationServer} on the primary JVM. */
    public static int getActivationPort() {
        int p = activationPort;
        if (p <= 0) {
            return getConfiguredPort() + 10_000;
        }
        return p;
    }

    /**
     * Call from {@code main} before {@code SpringApplication.run}. May call {@link Runtime#halt(int)} and
     * never return.
     */
    public static void prepareBeforeSpring(String[] args) {
        Properties defaults = loadClasspathApplicationProperties();
        desktopMode = resolveDesktopMode(defaults, args);
        desktopFullscreen = resolveDesktopFullscreen(defaults, args);
        desktopAllowWindowClose = resolveDesktopAllowWindowClose(defaults, args);
        singleInstanceEnabled = resolveSingleInstance(defaults, args);
        configuredPort = resolvePort(defaults, args);
        activationPort = resolveActivationPort(defaults, args, configuredPort);
        if (!singleInstanceEnabled) {
            return;
        }
        String dataDir = resolveDataDir(defaults, args);
        Path lockPath = Paths.get(dataDir, "production-calling-system.lock");
        try {
            Files.createDirectories(lockPath.getParent());
            RandomAccessFile raf = new RandomAccessFile(lockPath.toFile(), "rw");
            FileChannel channel = raf.getChannel();
            FileLock lock = channel.tryLock();
            if (lock == null) {
                channel.close();
                raf.close();
                log.info(
                        "Another instance holds the lock at {}; notifying running instance (port {}).",
                        lockPath,
                        configuredPort);
                delegateToRunningInstance(configuredPort);
            }
            lockHolder = raf;
        } catch (Exception e) {
            log.warn("Could not use single-instance lock at {}: {}", lockPath, e.getMessage());
            if (isLocalTcpPortListening(configuredPort)) {
                log.info("Port {} is accepting connections; notifying running instance.", configuredPort);
                delegateToRunningInstance(configuredPort);
            }
            throw new IllegalStateException("Single-instance is on but lock failed and port is not in use: " + lockPath, e);
        }
    }

    public static boolean isLikelyPortBindFailure(Throwable error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof java.net.BindException) {
                return true;
            }
            String simple = t.getClass().getSimpleName();
            if (simple.contains("PortInUse")) {
                return true;
            }
            String m = t.getMessage();
            if (m != null) {
                String lower = m.toLowerCase();
                if (lower.contains("address already in use")) {
                    return true;
                }
                if (lower.contains("port") && lower.contains("in use")) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static void delegateToRunningInstance(int port) {
        RunningInstanceNotifier.notifyRunningInstance(port);
        Runtime.getRuntime().halt(0);
    }

    private static boolean isLocalTcpPortListening(int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static Properties loadClasspathApplicationProperties() {
        Properties p = new Properties();
        try (InputStream in =
                Thread.currentThread().getContextClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException e) {
            log.debug("No classpath application.properties: {}", e.getMessage());
        }
        return p;
    }

    private static boolean resolveDesktopMode(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "system.desktop-mode"),
                System.getProperty("system.desktop-mode"),
                defaults.getProperty("system.desktop-mode"));
        return v != null && Boolean.parseBoolean(v.trim());
    }

    private static boolean resolveDesktopFullscreen(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "system.desktop-fullscreen"),
                System.getProperty("system.desktop-fullscreen"),
                defaults.getProperty("system.desktop-fullscreen"));
        if (v == null || v.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private static boolean resolveDesktopAllowWindowClose(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "system.desktop-allow-window-close"),
                System.getProperty("system.desktop-allow-window-close"),
                defaults.getProperty("system.desktop-allow-window-close"));
        if (v == null || v.isBlank()) {
            return true;
        }
        return Boolean.parseBoolean(v.trim());
    }

    private static boolean resolveSingleInstance(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "system.single-instance"),
                System.getProperty("system.single-instance"),
                defaults.getProperty("system.single-instance"));
        return v != null && Boolean.parseBoolean(v.trim());
    }

    private static int resolveActivationPort(Properties defaults, String[] args, int serverPort) {
        String v = firstNonBlank(
                argValue(args, "system.desktop-activation-port"),
                System.getProperty("system.desktop-activation-port"),
                defaults.getProperty("system.desktop-activation-port"));
        if (v != null && !v.isBlank()) {
            try {
                return Integer.parseInt(v.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid system.desktop-activation-port '{}'; using HTTP port + 10000.", v);
            }
        }
        return serverPort + 10_000;
    }

    private static int resolvePort(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "server.port"),
                System.getProperty("server.port"),
                defaults.getProperty("server.port"));
        if (v == null || v.isBlank()) {
            return 8080;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    private static String resolveDataDir(Properties defaults, String[] args) {
        String v = firstNonBlank(
                argValue(args, "system.data-dir"),
                System.getProperty("system.data-dir"),
                defaults.getProperty("system.data-dir"));
        if (v == null || v.isBlank()) {
            v = Paths.get(System.getProperty("user.home"), "production-calling-system-data").toString();
        } else {
            v = expandPlaceholders(v.trim());
        }
        return v;
    }

    private static String expandPlaceholders(String raw) {
        String home = System.getProperty("user.home", "");
        if (raw.contains("${user.home}")) {
            return raw.replace("${user.home}", home);
        }
        return raw;
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c;
            }
        }
        return null;
    }

    /** Supports {@code --key=value} (Spring Boot style). */
    private static String argValue(String[] args, String key) {
        if (args == null) {
            return null;
        }
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length()).trim();
            }
        }
        return null;
    }
}
