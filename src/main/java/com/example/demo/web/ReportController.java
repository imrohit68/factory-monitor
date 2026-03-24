package com.example.demo.web;

import com.example.demo.service.AndonEventExportService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.time.LocalDate;

@Controller
@RequestMapping("/report")
public class ReportController {

    private final AndonEventExportService exportService;

    public ReportController(AndonEventExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public String reportPage() {
        return "redirect:/admin/event-log";
    }

    @GetMapping("/export.csv")
    public void exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            HttpServletResponse response)
            throws IOException {
        try {
            exportService.validateRange(start, end);
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }
        String filename = String.format("andon-event-log-%s-to-%s.csv", start, end);
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        exportService.writeCsv(start, end, response.getOutputStream());
        response.flushBuffer();
    }
}
