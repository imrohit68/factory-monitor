package com.example.demo.web;

import com.example.demo.service.AndonOrchestrationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final AndonOrchestrationService orchestration;

    public DashboardController(AndonOrchestrationService orchestration) {
        this.orchestration = orchestration;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("modbusConnected", orchestration.isModbusConnected());
        model.addAttribute("modbusError", orchestration.getModbusLastError());
        return "dashboard";
    }
}
