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
import java.util.concurrent.TimeUnit;

/**
 * Transcodes alert audio to MP3 using FFmpeg (required for OGG in JavaFX WebView).
 */
@Service
@RequiredArgsConstructor
public class AudioFfmpegService {

    private static final Logger log = LoggerFactory.getLogger(AudioFfmpegService.class);

    private static final Object TRANSCODE_LOCK = new Object();

    private final AppProperties appProperties;

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
        String ffmpeg = resolveFfmpegExecutable();
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
        cmd.add(targetMp3.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new IOException(
                    "Could not start FFmpeg ('" + ffmpeg + "'). Install FFmpeg or set system.audio-ffmpeg-path.", e);
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

    public String resolveFfmpegExecutable() {
        String configured = appProperties.getAudioFfmpegPath();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return "ffmpeg";
    }
}
