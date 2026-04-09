package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.service.AudioFfmpegService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Serves uploaded alert audio. OGG/OGA and legacy WAV URLs redirect to an MP3 sidecar, creating it via FFmpeg if
 * needed, so JavaFX WebView can play alerts.
 */
@RestController
@RequiredArgsConstructor
public class AudioUploadController {

    private final AppProperties appProperties;
    private final AudioFfmpegService audioFfmpegService;

    @GetMapping("/audio/uploads/{fileName:.+}")
    public ResponseEntity<?> serve(@PathVariable String fileName) {
        if (!isSafeFileName(fileName)) {
            return ResponseEntity.badRequest().build();
        }
        Path base = Path.of(appProperties.getAudioUploadDir()).toAbsolutePath().normalize();
        Path file = base.resolve(fileName).normalize();
        if (!file.startsWith(base)) {
            return ResponseEntity.badRequest().build();
        }

        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ogg") || lower.endsWith(".oga")) {
            return serveOggAlias(fileName, base);
        }
        if (lower.endsWith(".wav")) {
            return serveWavAlias(fileName, base);
        }

        if (!lower.endsWith(".mp3")) {
            return ResponseEntity.notFound().build();
        }

        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }

        Resource body = new FileSystemResource(file);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(body);
    }

    private ResponseEntity<?> serveOggAlias(String fileName, Path base) {
        String stem = fileName.substring(0, fileName.length() - 4);
        Path mp3 = base.resolve(stem + ".mp3");
        if (Files.isRegularFile(mp3)) {
            return redirectToMp3(stem);
        }
        Path oggLike = base.resolve(fileName);
        if (!Files.isRegularFile(oggLike)) {
            return ResponseEntity.notFound().build();
        }
        try {
            audioFfmpegService.transcodeToMp3(oggLike, mp3);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("Could not transcode OGG to MP3. Install FFmpeg or set system.audio-ffmpeg-path.");
        }
        if (!Files.isRegularFile(mp3)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return redirectToMp3(stem);
    }

    private ResponseEntity<?> serveWavAlias(String fileName, Path base) {
        String stem = fileName.substring(0, fileName.length() - 4);
        Path mp3 = base.resolve(stem + ".mp3");
        if (Files.isRegularFile(mp3)) {
            return redirectToMp3(stem);
        }
        Path wav = base.resolve(fileName);
        if (!Files.isRegularFile(wav)) {
            return ResponseEntity.notFound().build();
        }
        try {
            audioFfmpegService.transcodeToMp3(wav, mp3);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                    .body("Could not transcode WAV to MP3. Install FFmpeg or set system.audio-ffmpeg-path.");
        }
        if (!Files.isRegularFile(mp3)) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        return redirectToMp3(stem);
    }

    private static ResponseEntity<Void> redirectToMp3(String stem) {
        URI loc =
                ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path("/audio/uploads/")
                        .path(stem + ".mp3")
                        .build()
                        .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(loc).build();
    }

    private static boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.isBlank()
                && !fileName.contains("..")
                && !fileName.contains("/")
                && !fileName.contains("\\");
    }
}
