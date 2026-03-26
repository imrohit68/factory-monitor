package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final OrchestrationService orchestration;
    private final AppProperties appProperties;

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("modbusConnected", orchestration.isModbusConnected());
        model.addAttribute("modbusError", orchestration.getModbusLastError());
        model.addAttribute("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        model.addAttribute("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        return "dashboard";
    }
}
