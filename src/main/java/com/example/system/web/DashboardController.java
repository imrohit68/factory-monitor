package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final OrchestrationService orchestration;
    private final AppProperties appProperties;

    @GetMapping("/")
    public String dashboard(Model model, HttpServletResponse response) {
        // Avoid serving a stale dashboard shell from browser cache when user navigates back;
        // without a fresh load, Vue may not run and raw {{ }} placeholders appear.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");

        model.addAttribute("modbusConnected", orchestration.isModbusConnected());
        model.addAttribute("modbusError", orchestration.getModbusLastError());
        model.addAttribute("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        model.addAttribute("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        return "dashboard";
    }
}
