package com.example.system.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ensures {@code system.data-dir} exists before the JDBC URL is used (SQLite file path).
 */
public class DataDirectoryInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        String dataDir = env.getProperty("system.data-dir");
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = Path.of(System.getProperty("user.home"), "production-calling-system-data").toString();
        }
        String audioDir = env.getProperty("system.audio-upload-dir");
        if (audioDir == null || audioDir.isBlank()) {
            audioDir = Path.of(dataDir, "alert-audio").toString();
        }
        String previewDir = env.getProperty("system.audio-preview-dir");
        if (previewDir == null || previewDir.isBlank()) {
            previewDir = Path.of(dataDir, "alert-audio-preview").toString();
        }
        try {
            Files.createDirectories(Path.of(dataDir));
            Files.createDirectories(Path.of(dataDir, "logs"));
            Files.createDirectories(Path.of(audioDir));
            Files.createDirectories(Path.of(previewDir));
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot create data directories (data-dir="
                            + dataDir
                            + ", audio="
                            + audioDir
                            + ", preview="
                            + previewDir
                            + ")",
                    e);
        }
    }
}
