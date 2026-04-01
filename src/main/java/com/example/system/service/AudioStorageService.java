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

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    "mp3",
                    "wav",
                    "ogg",
                    "oga",
                    "opus",
                    "m4a",
                    "aac",
                    "caf",
                    "mp4",
                    "webm",
                    "flac",
                    "wma",
                    "aif",
                    "aiff",
                    "alac",
                    "m4b");

    private final AppProperties appProperties;

    /**
     * Saves the file under {@link AppProperties#getAudioUploadDir()} and returns a public URL path
     * such as {@code /audio/uploads/<uuid>.mp3}, or null if empty.
     *
     * @throws IOException if the file is not an allowed audio type
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
                    "Only audio files are allowed (e.g. MP3, WAV, M4A, OGG, FLAC). "
                            + "If your file is valid audio, convert or rename it to a standard extension.");
        }
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
            if (ext.length() > 8) {
                ext = "";
            }
        }
        Path dir = Path.of(appProperties.getAudioUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + ext;
        Path dest = dir.resolve(name);
        file.transferTo(dest);
        return "/audio/uploads/" + name;
    }

    static boolean isAllowedAudioUpload(String extensionWithoutDot, String contentType) {
        if (extensionWithoutDot != null && !extensionWithoutDot.isBlank()) {
            if (ALLOWED_EXTENSIONS.contains(extensionWithoutDot.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        if (contentType != null && !contentType.isBlank()) {
            String ct = contentType.toLowerCase(Locale.ROOT).trim();
            if (ct.startsWith("audio/")) {
                return true;
            }
        }
        return false;
    }

    private static String extensionWithoutDot(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1);
        if (ext.length() > 8) {
            return "";
        }
        return ext;
    }
}
