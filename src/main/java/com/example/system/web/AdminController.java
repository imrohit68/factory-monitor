package com.example.system.web;

import com.example.system.dto.EventLogPageData;
import com.example.system.service.AdminViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminViewService adminViewService;

    @GetMapping
    public String adminHome() {
        return "redirect:/admin/workstations";
    }

    @GetMapping("/event-log")
    public String eventLog(Model model) {
        EventLogPageData page = adminViewService.eventLogPageData();
        model.addAttribute("defaultStart", page.defaultStart());
        model.addAttribute("defaultEnd", page.defaultEnd());
        model.addAttribute("maxRangeDays", page.maxRangeDays());
        return "admin/event-log";
    }
}
