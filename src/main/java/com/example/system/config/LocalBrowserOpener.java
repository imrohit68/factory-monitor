package com.example.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Opens the local dashboard URL, preferring Chromium-based browsers with {@code --start-fullscreen} so the
 * floor display opens edge-to-edge. Falls back to the OS default browser when no known binary is available.
 */
public final class LocalBrowserOpener {

    private static final Logger log = LoggerFactory.getLogger(LocalBrowserOpener.class);

    private LocalBrowserOpener() {}

    public static void openLocalDashboard(int port) {
        String url = "http://127.0.0.1:" + port;
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
                if (tryStartFullscreenChromiumWindows(url)) {
                    return;
                }
                startProcess(
                        new ProcessBuilder("cmd", "/c", "start", "", url),
                        "default browser (Windows start)");
            } else if (os.contains("mac")) {
                if (tryStartFullscreenChromiumMac(url)) {
                    return;
                }
                startProcess(new ProcessBuilder("open", url), "open (macOS default)");
            } else {
                if (tryStartFullscreenChromiumLinux(url)) {
                    return;
                }
                startProcess(new ProcessBuilder("xdg-open", url), "xdg-open");
            }
        } catch (IOException | SecurityException e) {
            log.warn("Could not open browser at {}: {}", url, e.getMessage());
        }
    }

    private static boolean tryStartFullscreenChromiumWindows(String url) throws IOException {
        List<Path> candidates = new ArrayList<>();
        String pf = System.getenv("ProgramFiles");
        String pfx86 = System.getenv("ProgramFiles(x86)");
        String local = System.getenv("LOCALAPPDATA");
        if (pfx86 != null && !pfx86.isBlank()) {
            candidates.add(
                    Path.of(pfx86, "Microsoft", "Edge", "Application", "msedge.exe"));
        }
        if (pf != null && !pf.isBlank()) {
            candidates.add(Path.of(pf, "Microsoft", "Edge", "Application", "msedge.exe"));
            candidates.add(Path.of(pf, "Google", "Chrome", "Application", "chrome.exe"));
        }
        if (local != null && !local.isBlank()) {
            candidates.add(Path.of(local, "Google", "Chrome", "Application", "chrome.exe"));
        }
        for (Path exe : candidates) {
            if (!Files.isRegularFile(exe)) {
                continue;
            }
            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--start-fullscreen", url);
            if (startProcessQuietly(pb)) {
                log.info("Opened dashboard in fullscreen via {}", exe);
                return true;
            }
        }
        return false;
    }

    private static boolean tryStartFullscreenChromiumMac(String url) throws IOException {
        String[][] apps = {
            {"Microsoft Edge", "Microsoft Edge"},
            {"Google Chrome", "Google Chrome"},
            {"Chromium", "Chromium"}
        };
        for (String[] app : apps) {
            ProcessBuilder pb =
                    new ProcessBuilder(
                            "open", "-a", app[0], "--args", "--start-fullscreen", url);
            if (startProcessQuietly(pb)) {
                log.info("Opened dashboard in fullscreen via {}", app[1]);
                return true;
            }
        }
        return false;
    }

    private static boolean tryStartFullscreenChromiumLinux(String url) throws IOException {
        String[] cmds = {
            "microsoft-edge-stable",
            "microsoft-edge",
            "google-chrome-stable",
            "google-chrome",
            "chromium",
            "chromium-browser"
        };
        for (String cmd : cmds) {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--start-fullscreen", url);
            if (startProcessQuietly(pb)) {
                log.info("Opened dashboard in fullscreen via {}", cmd);
                return true;
            }
        }
        return false;
    }

    /**
     * Starts the process and returns true if launch likely succeeded: still running after 2s, or exited with code 0
     * (typical for {@code open} / Chrome launcher stubs).
     */
    private static boolean startProcessQuietly(ProcessBuilder pb) {
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        // Do not use redirectInput(DISCARD): JDK rejects it ("Redirect invalid for reading: WRITE").
        try {
            Process p = pb.start();
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                return true;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            log.debug("Fullscreen browser candidate failed: {}", e.getMessage());
            return false;
        }
    }

    private static void startProcess(ProcessBuilder pb, String label) throws IOException {
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        pb.start();
        log.info("Opened dashboard via {}", label);
    }
}
