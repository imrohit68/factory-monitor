package com.example.demo.service;

import com.example.demo.config.AndonAppProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class AudioStorageService {

    private final AndonAppProperties andon;

    public AudioStorageService(AndonAppProperties andon) {
        this.andon = andon;
    }

    /**
     * Saves the file under {@link AndonAppProperties#getAudioUploadDir()} and returns a public URL path
     * such as {@code /audio/uploads/<uuid>.mp3}, or null if empty.
     */
    public String storeUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path dir = Path.of(andon.getAudioUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
            if (ext.length() > 8) {
                ext = "";
            }
        }
        String name = UUID.randomUUID() + ext;
        Path dest = dir.resolve(name);
        file.transferTo(dest);
        return "/audio/uploads/" + name;
    }
}
