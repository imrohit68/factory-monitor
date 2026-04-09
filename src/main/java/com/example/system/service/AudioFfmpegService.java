package com.example.system.service;

import com.example.system.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Transcodes alert audio to MP3 using FFmpeg (required for OGG in JavaFX WebView).
 */
@Service
@RequiredArgsConstructor
public class AudioFfmpegService {

    private static final Logger log = LoggerFactory.getLogger(AudioFfmpegService.class);

    private static final Object TRANSCODE_LOCK = new Object();
    private static final Object DISCOVERY_LOCK = new Object();

    private final AppProperties appProperties;

    /** When {@code system.audio-ffmpeg-path} is unset, first successful probe is cached for this JVM. */
    private volatile String discoveredFfmpeg;

    /**
     * Converts any FFmpeg-readable input to MP3 (libmp3lame). Overwrites {@code targetMp3} if present.
     */
    public void transcodeToMp3(Path source, Path targetMp3) throws IOException {
        synchronized (TRANSCODE_LOCK) {
            Files.createDirectories(targetMp3.getParent());
            Path tempOut = targetMp3.resolveSibling(targetMp3.getFileName().toString() + ".tmp");
            try {
                runFfmpegToMp3(source, tempOut);
                Files.move(tempOut, targetMp3, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(tempOut);
                } catch (IOException ignored) {
                    log.debug("Could not delete temp transcode file: {}", ignored.getMessage());
                }
                throw e;
            }
        }
    }

    private void runFfmpegToMp3(Path source, Path targetMp3) throws IOException {
        String ffmpeg = effectiveFfmpegExecutable();
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-y");
        cmd.add("-nostdin");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-i");
        cmd.add(source.toAbsolutePath().toString());
        cmd.add("-codec:a");
        cmd.add("libmp3lame");
        cmd.add("-q:a");
        cmd.add("4");
        cmd.add("-f");
        cmd.add("mp3");
        cmd.add(targetMp3.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            discoveredFfmpeg = null;
            throw new IOException(
                    "Could not start FFmpeg ('"
                            + ffmpeg
                            + "'). Install FFmpeg or set system.audio-ffmpeg-path to the full path of the executable.",
                    e);
        }
        try {
            boolean finished = p.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IOException("FFmpeg timed out while transcoding to MP3.");
            }
            int code = p.exitValue();
            if (code != 0) {
                String err = readStream(p.getInputStream());
                throw new IOException(
                        "FFmpeg failed (exit " + code + "): " + (err.isBlank() ? "no output" : err.trim()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new IOException("FFmpeg interrupted.", e);
        }
    }

    private static String readStream(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Resolves FFmpeg: explicit {@code system.audio-ffmpeg-path}, else PATH {@code ffmpeg}, then common install
     * locations (e.g. Homebrew on macOS). Caches the discovered path so IDE launches with a minimal PATH still
     * work after the first successful probe.
     */
    private String effectiveFfmpegExecutable() throws IOException {
        String configured = appProperties.getAudioFfmpegPath();
        if (configured != null && !configured.isBlank()) {
            String c = configured.trim();
            if (!ffmpegVersionWorks(c)) {
                throw new IOException(
                        "system.audio-ffmpeg-path is set but FFmpeg did not run successfully: "
                                + c
                                + ". Check the path or install FFmpeg.");
            }
            return c;
        }
        if (discoveredFfmpeg != null) {
            return discoveredFfmpeg;
        }
        synchronized (DISCOVERY_LOCK) {
            if (discoveredFfmpeg != null) {
                return discoveredFfmpeg;
            }
            for (String candidate : defaultFfmpegCandidates()) {
                if (ffmpegVersionWorks(candidate)) {
                    discoveredFfmpeg = candidate;
                    log.info("Using FFmpeg executable: {}", candidate);
                    return candidate;
                }
            }
        }
        throw new IOException(
                "FFmpeg not found (needed for OGG uploads). Install it and/or fix your PATH, or set "
                        + "system.audio-ffmpeg-path. Examples: macOS: brew install ffmpeg (often "
                        + "/opt/homebrew/bin/ffmpeg). Windows: winget install ffmpeg. "
                        + "If you run from an IDE, add FFmpeg to PATH or set the full path in configuration.");
    }

    private static List<String> defaultFfmpegCandidates() {
        List<String> list = new ArrayList<>();
        list.add("ffmpeg");
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac")) {
            list.add("/opt/homebrew/bin/ffmpeg");
            list.add("/usr/local/bin/ffmpeg");
        }
        if (os.contains("windows")) {
            list.add("C:\\Program Files\\ffmpeg\\bin\\ffmpeg.exe");
            list.add("C:\\ffmpeg\\bin\\ffmpeg.exe");
        }
        if (os.contains("linux")) {
            list.add("/usr/bin/ffmpeg");
        }
        return list;
    }

    private static boolean ffmpegVersionWorks(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try {
                boolean done = p.waitFor(8, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    return false;
                }
                return p.exitValue() == 0;
            } finally {
                drainQuietly(p.getInputStream());
            }
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void drainQuietly(InputStream in) {
        if (in == null) {
            return;
        }
        try {
            in.readAllBytes();
        } catch (IOException ignored) {
            // ignore
        }
    }
}
