package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.config.BrowserCloseShutdownService;
import lombok.RequiredArgsConstructor;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Keeps desktop heartbeats alive on every HTML page (dashboard, login, admin) so navigating away from the
 * Vue app does not look like a closed tab.
 */
@ControllerAdvice(
        assignableTypes = {
            DashboardController.class,
            LoginController.class,
            AdminController.class,
            WorkstationAdminController.class,
            DeviceConfigController.class,
            ReportController.class
        })
@RequiredArgsConstructor
public class DesktopHeartbeatModelAdvice {

    private final AppProperties appProperties;
    private final BrowserCloseShutdownService browserCloseShutdownService;

    @ModelAttribute
    public void desktopHeartbeatAttributes(Model model) {
        boolean on = browserCloseShutdownService.isExitOnBrowserClose();
        model.addAttribute("desktopHeartbeatEnabled", on);
        if (on) {
            model.addAttribute("desktopSessionToken", browserCloseShutdownService.getSessionToken());
            int ms = Math.max(1000, appProperties.getBrowserHeartbeatIntervalSeconds() * 1000);
            model.addAttribute("desktopHeartbeatIntervalMs", ms);
        }
    }
}
