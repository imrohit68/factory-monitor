package com.example.system.service;

import com.example.system.config.AppProperties;
import com.example.system.repository.WorkstationSlotRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);

    private static final String UPLOAD_PUBLIC_PREFIX = "/audio/uploads/";

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
    private final WorkstationSlotRepository workstationSlotRepository;

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

    /**
     * Deletes a file under the upload directory if {@code publicPath} is a managed {@code /audio/uploads/...} URL and
     * no {@code workstation_slot} row still references that path.
     */
    public void deleteManagedUploadFileIfUnreferenced(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return;
        }
        String normalized = publicPath.trim();
        if (!isManagedUploadPublicPath(normalized)) {
            return;
        }
        Path base = Path.of(appProperties.getAudioUploadDir()).toAbsolutePath().normalize();
        String fileName = normalized.substring(UPLOAD_PUBLIC_PREFIX.length());
        Path file = base.resolve(fileName).normalize();
        if (!file.startsWith(base)) {
            return;
        }
        if (workstationSlotRepository.countByAudioPath(normalized) > 0) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.debug("Could not delete unreferenced upload {}: {}", file, e.getMessage());
        }
    }

    /**
     * After the current transaction commits, run {@link #deleteManagedUploadFileIfUnreferenced(String)} for each
     * distinct path (typically superseded audio paths when a workstation slot was updated or cleared).
     */
    public void scheduleDeleteManagedUploadFilesIfUnreferencedAfterCommit(Collection<String> publicPaths) {
        if (publicPaths == null || publicPaths.isEmpty()) {
            return;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String p : publicPaths) {
            if (p != null && !p.isBlank()) {
                unique.add(p.trim());
            }
        }
        if (unique.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("No active transaction; deleting unreferenced uploads immediately for {} path(s)", unique.size());
            for (String p : unique) {
                deleteManagedUploadFileIfUnreferenced(p);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        for (String p : unique) {
                            deleteManagedUploadFileIfUnreferenced(p);
                        }
                    }
                });
    }

    static boolean isManagedUploadPublicPath(String publicPath) {
        if (publicPath == null || !publicPath.startsWith(UPLOAD_PUBLIC_PREFIX)) {
            return false;
        }
        String name = publicPath.substring(UPLOAD_PUBLIC_PREFIX.length());
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.contains("..")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".oga");
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
