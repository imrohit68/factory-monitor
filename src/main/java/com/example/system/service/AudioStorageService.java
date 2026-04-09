package com.example.system.service;

import com.example.system.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("mp3", "wav", "ogg");

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of(
                    "audio/mpeg",
                    "audio/mp3",
                    "audio/wav",
                    "audio/wave",
                    "audio/x-wav",
                    "audio/ogg",
                    "application/ogg");

    private final AppProperties appProperties;
    private final AudioFfmpegService audioFfmpegService;

    /**
     * Saves alert audio under {@link AppProperties#getAudioUploadDir()} and returns a public URL path
     * such as {@code /audio/uploads/<uuid>.mp3}. OGG and WAV uploads are transcoded to MP3 for JavaFX WebView playback.
     *
     * @throws IOException if the file is not an allowed type or FFmpeg is required but fails
     */
    public String storeUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String original = file.getOriginalFilename();
        String extNoDot = extensionWithoutDot(original);
        String mime = file.getContentType();
        if (!isAllowedAudioUpload(extNoDot, mime)) {
            throw new IOException(
                    "Only MP3, WAV, and OGG alert audio files are allowed. Convert other formats before uploading.");
        }
        String extLower = extNoDot.toLowerCase(Locale.ROOT);
        Path dir = Path.of(appProperties.getAudioUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String id = UUID.randomUUID().toString();

        if ("ogg".equals(extLower)) {
            Path tempOgg = Files.createTempFile(dir, "upload-" + id + "-", ".ogg");
            try {
                file.transferTo(tempOgg);
                Path mp3 = dir.resolve(id + ".mp3");
                audioFfmpegService.transcodeToMp3(tempOgg, mp3);
                return "/audio/uploads/" + id + ".mp3";
            } finally {
                try {
                    Files.deleteIfExists(tempOgg);
                } catch (IOException e) {
                    // best-effort cleanup
                }
            }
        }

        if ("wav".equals(extLower)) {
            Path tempWav = Files.createTempFile(dir, "upload-" + id + "-", ".wav");
            try {
                file.transferTo(tempWav);
                Path mp3 = dir.resolve(id + ".mp3");
                audioFfmpegService.transcodeToMp3(tempWav, mp3);
                return "/audio/uploads/" + id + ".mp3";
            } finally {
                try {
                    Files.deleteIfExists(tempWav);
                } catch (IOException e) {
                    // best-effort cleanup
                }
            }
        }

        String storedName = id + "." + extLower;
        Path dest = dir.resolve(storedName);
        file.transferTo(dest);
        return "/audio/uploads/" + storedName;
    }

    static boolean isAllowedAudioUpload(String extensionWithoutDot, String contentType) {
        String ext = extensionWithoutDot != null ? extensionWithoutDot.toLowerCase(Locale.ROOT).trim() : "";
        if (!ext.isEmpty()) {
            return ALLOWED_EXTENSIONS.contains(ext);
        }
        if (contentType == null || contentType.isBlank()) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT).trim();
        int semi = ct.indexOf(';');
        if (semi >= 0) {
            ct = ct.substring(0, semi).trim();
        }
        return ALLOWED_CONTENT_TYPES.contains(ct);
    }

    private static String extensionWithoutDot(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return "";
        }
        String e = originalFilename.substring(dot + 1);
        if (e.length() > 8) {
            return "";
        }
        return e;
    }
}
