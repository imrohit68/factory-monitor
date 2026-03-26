package com.example.system.web;

import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardApiController {

    private final OrchestrationService orchestration;

    @GetMapping
    public Map<String, Object> dashboard() {
        return orchestration.buildDashboardApiResponse();
    }
}
