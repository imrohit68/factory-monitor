package com.example.system.service;

import com.example.system.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Short-lived MP3 files for workstation form preview (WAV/OGG in JavaFX WebView). Cleaned up periodically.
 */
@Service
@RequiredArgsConstructor
public class AudioAlertPreviewService {

    private static final Pattern SAFE_PREVIEW_ID = Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");

    private final AppProperties appProperties;
    private final AudioFfmpegService audioFfmpegService;

    @PostConstruct
    void purgeOnStartup() {
        purgeExpired();
    }

    @Scheduled(cron = "0 23 * * * *")
    void purgeHourly() {
        purgeExpired();
    }

    /**
     * Writes an MP3 under the preview directory and returns the id (without {@code .mp3}).
     *
     * @throws IOException if validation fails, IO fails, or transcoding fails
     */
    public String createPreviewMp3(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IOException("No file uploaded.");
        }
        String original = file.getOriginalFilename();
        String extNoDot = extensionWithoutDot(original);
        String mime = file.getContentType();
        if (!AudioStorageService.isAllowedAudioUpload(extNoDot, mime)) {
            throw new IOException(
                    "Only MP3, WAV, and OGG alert audio files are allowed. Convert other formats before uploading.");
        }
        String extLower = extNoDot.toLowerCase(Locale.ROOT);
        Path dir = Path.of(appProperties.getAudioPreviewDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String id = UUID.randomUUID().toString();
        Path outMp3 = dir.resolve(id + ".mp3");

        if ("mp3".equals(extLower)) {
            file.transferTo(outMp3);
            return id;
        }

        Path tempIn =
                Files.createTempFile(dir, "preview-in-" + id + "-", "." + extLower);
        try {
            file.transferTo(tempIn);
            audioFfmpegService.transcodeToMp3(tempIn, outMp3);
        } finally {
            try {
                Files.deleteIfExists(tempIn);
            } catch (IOException e) {
                // best-effort
            }
        }
        if (!Files.isRegularFile(outMp3)) {
            throw new IOException("Preview transcoding did not produce an MP3 file.");
        }
        return id;
    }

    public Path resolvePreviewMp3(String id) {
        if (id == null || !SAFE_PREVIEW_ID.matcher(id).matches()) {
            return null;
        }
        Path base = Path.of(appProperties.getAudioPreviewDir()).toAbsolutePath().normalize();
        Path file = base.resolve(id + ".mp3").normalize();
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            return null;
        }
        return file;
    }

    /**
     * Removes a preview MP3 if present. Invalid ids or paths outside the preview directory are ignored.
     *
     * @return true if a file was deleted
     */
    public boolean deletePreviewMp3(String id) {
        if (id == null || !SAFE_PREVIEW_ID.matcher(id).matches()) {
            return false;
        }
        Path base = Path.of(appProperties.getAudioPreviewDir()).toAbsolutePath().normalize();
        Path file = base.resolve(id + ".mp3").normalize();
        if (!file.startsWith(base)) {
            return false;
        }
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }

    void purgeExpired() {
        int hours = appProperties.getAudioPreviewRetentionHours();
        if (hours <= 0) {
            return;
        }
        Path base = Path.of(appProperties.getAudioPreviewDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(base)) {
            return;
        }
        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        try (var stream = Files.list(base)) {
            stream.forEach(
                    p -> {
                        try {
                            if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".mp3")) {
                                return;
                            }
                            FileTime lm = Files.getLastModifiedTime(p);
                            if (lm.toInstant().isBefore(cutoff)) {
                                Files.deleteIfExists(p);
                            }
                        } catch (IOException e) {
                            // best-effort per file
                        }
                    });
        } catch (IOException e) {
            // best-effort
        }
    }

    private static String extensionWithoutDot(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return "";
        }
        String e = originalFilename.substring(dot + 1).trim();
        if (e.length() > 8) {
            return "";
        }
        return e;
    }
}
