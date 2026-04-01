package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
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
    public String dashboard(
            Model model,
            HttpServletResponse response,
            Authentication authentication,
            CsrfToken csrfToken) {
        // Avoid serving a stale dashboard shell from browser/WebView cache when returning from admin;
        // without a fresh load, Vue may not run and raw {{ }} placeholders appear.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        boolean signedIn =
                authentication != null
                        && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken);
        model.addAttribute("dashboardSignedIn", signedIn);
        if (signedIn && authentication != null) {
            model.addAttribute("dashboardUsername", authentication.getName());
        }
        if (csrfToken != null) {
            model.addAttribute("_csrf", csrfToken);
        }

        model.addAttribute("modbusConnected", orchestration.isModbusConnected());
        model.addAttribute("modbusError", orchestration.getModbusLastError());
        model.addAttribute("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        model.addAttribute("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        return "dashboard";
    }
}
