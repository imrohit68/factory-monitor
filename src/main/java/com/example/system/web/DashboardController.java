package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.config.SingleInstanceSupport;
import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    /**
     * Browser {@code sessionStorage} key set from {@code dashboard.html} after the matrix loads. The gate page
     * redirects to {@code /dashboard} when present so “Return to dashboard” in the same tab skips the gate, while
     * a new tab has no key and shows the gate again (autoplay policy).
     */
    public static final String FLOOR_AUDIO_GESTURE_SESSION_STORAGE_KEY = "pcs_floor_audio_gesture";

    private final OrchestrationService orchestration;
    private final AppProperties appProperties;

    /**
     * Browser: gate at {@code /} (with client-side skip via sessionStorage when this tab already opened the matrix).
     * JavaFX WebView skips via {@code ?wv=1} or JavaFX User-Agent.
     */
    @GetMapping("/")
    public String root(HttpServletRequest request, HttpServletResponse response, Model model) {
        applyNoStoreHeaders(response);
        if (SingleInstanceSupport.isDesktopMode() && isDesktopEmbeddedWebClient(request)) {
            applyDashboardModel(model);
            return "dashboard";
        }
        return "dashboard-gate";
    }

    /**
     * Initial {@link com.example.system.desktop.FactoryMonitorDesktopApplication} load uses {@code ?wv=1}; in-app
     * navigation relies on WebView sending a User-Agent that contains {@code JavaFX}.
     */
    private static boolean isDesktopEmbeddedWebClient(HttpServletRequest request) {
        if ("1".equals(request.getParameter("wv"))) {
            return true;
        }
        String ua = request.getHeader("User-Agent");
        return ua != null && ua.toLowerCase(Locale.ROOT).contains("javafx");
    }

    /**
     * Floor matrix URL for browsers (after the gate). Same model as {@code /} when running in the JavaFX desktop
     * shell.
     */
    @GetMapping("/dashboard")
    public String dashboard(HttpServletResponse response, Model model) {
        applyNoStoreHeaders(response);
        applyDashboardModel(model);
        return "dashboard";
    }

    private void applyDashboardModel(Model model) {
        model.addAttribute("modbusConnected", orchestration.isModbusConnected());
        model.addAttribute("modbusError", orchestration.getModbusLastError());
        model.addAttribute("alertRepeatIntervalMinutes", appProperties.getDashboardAlertRepeatIntervalMinutes());
        model.addAttribute("alertMaxRepeats", appProperties.getDashboardAlertMaxRepeats());
        model.addAttribute("alertGapMs", appProperties.getDashboardAlertGapMs());
    }

    private static void applyNoStoreHeaders(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
    }
}
