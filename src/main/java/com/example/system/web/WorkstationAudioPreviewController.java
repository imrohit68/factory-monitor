package com.example.system.web;

import com.example.system.service.AudioAlertPreviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/admin/workstations/audio-preview")
@RequiredArgsConstructor
public class WorkstationAudioPreviewController {

    private final AudioAlertPreviewService audioAlertPreviewService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadPreview(@RequestParam("file") MultipartFile file) {
        try {
            String id = audioAlertPreviewService.createPreviewMp3(file);
            String url = "/admin/workstations/audio-preview/" + id + ".mp3";
            return ResponseEntity.ok(Map.of("url", url));
        } catch (IOException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Could not create preview.";
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", msg));
        }
    }

    @GetMapping("/{id}.mp3")
    public ResponseEntity<Resource> serve(@PathVariable String id) {
        Path path = audioAlertPreviewService.resolvePreviewMp3(id);
        if (path == null) {
            return ResponseEntity.notFound().build();
        }
        Resource body = new FileSystemResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /** Idempotent: missing or unknown ids still return 204. */
    @DeleteMapping("/{id}.mp3")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        audioAlertPreviewService.deletePreviewMp3(id);
        return ResponseEntity.noContent().build();
    }
}
