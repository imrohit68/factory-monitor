package com.example.demo.web;

import com.example.demo.config.AndonAppProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AndonAppProperties appProperties;

    public AdminController(AndonAppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @GetMapping
    public String adminHome() {
        return "redirect:/admin/workstations";
    }

    @GetMapping("/event-log")
    public String eventLog(Model model) {
        LocalDate today = LocalDate.now();
        model.addAttribute("defaultStart", today.minusDays(6));
        model.addAttribute("defaultEnd", today);
        model.addAttribute("maxRangeDays", appProperties.getLogRetentionDays());
        return "admin/event-log";
    }
}
