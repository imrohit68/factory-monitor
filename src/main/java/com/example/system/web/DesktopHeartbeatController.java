package com.example.system.web;

import com.example.system.config.BrowserCloseShutdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class DesktopHeartbeatController {

    private static final String TOKEN_HEADER = "X-Factory-Monitor-Token";

    private final BrowserCloseShutdownService browserCloseShutdownService;

    @PostMapping("/desktop-heartbeat")
    public ResponseEntity<Void> heartbeat(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        if (!browserCloseShutdownService.recordHeartbeatIfValid(token)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.noContent().build();
    }
}
