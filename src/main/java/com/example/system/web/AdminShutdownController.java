package com.example.system.web;

import com.example.system.config.ApplicationShutdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/shutdown")
@RequiredArgsConstructor
public class AdminShutdownController {

    private final ApplicationShutdownService applicationShutdownService;

    @GetMapping
    public String shutdownPage() {
        return "admin/shutdown";
    }

    @PostMapping
    public String shutdown() {
        applicationShutdownService.shutdownGracefully();
        return "admin/shutdown-ack";
    }
}
