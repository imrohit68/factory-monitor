package com.example.system.web;

import com.example.system.config.AppProperties;
import com.example.system.config.SingleInstanceSupport;
import com.example.system.service.OrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Duration;
import java.util.Locale;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    /** Set after the operator reaches {@code /dashboard} once; survives {@link com.example.system.config.DashboardSessionInvalidateFilter} (session cleared on floor views). */
    public static final String FLOOR_AUDIO_GESTURE_COOKIE = "PCS_FLOOR_AUDIO_OK";

    private static final String FLOOR_AUDIO_GESTURE_VALUE = "1";
    private static final Duration FLOOR_AUDIO_GESTURE_MAX_AGE = Duration.ofDays(30);

    private final OrchestrationService orchestration;
    private final AppProperties appProperties;

    /**
     * Gate at {@code /} for real browsers until they have opened the matrix once (cookie). Avoids showing the gate on
     * every “Return to Dashboard” from admin. JavaFX WebView skips via {@code ?wv=1} or JavaFX User-Agent.
     */
    @GetMapping("/")
    public String root(HttpServletRequest request, HttpServletResponse response, Model model) {
        applyNoStoreHeaders(response);
        if (SingleInstanceSupport.isDesktopMode() && isDesktopEmbeddedWebClient(request)) {
            applyDashboardModel(model);
            return "dashboard";
        }
        if (hasFloorAudioGestureCookie(request)) {
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
    public String dashboard(HttpServletRequest request, HttpServletResponse response, Model model) {
        applyNoStoreHeaders(response);
        addFloorAudioGestureCookie(response, request.isSecure());
        applyDashboardModel(model);
        return "dashboard";
    }

    static boolean hasFloorAudioGestureCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (FLOOR_AUDIO_GESTURE_COOKIE.equals(c.getName())
                    && FLOOR_AUDIO_GESTURE_VALUE.equals(c.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static void addFloorAudioGestureCookie(HttpServletResponse response, boolean requestSecure) {
        ResponseCookie cookie =
                ResponseCookie.from(FLOOR_AUDIO_GESTURE_COOKIE, FLOOR_AUDIO_GESTURE_VALUE)
                        .path("/")
                        .maxAge(FLOOR_AUDIO_GESTURE_MAX_AGE)
                        .httpOnly(true)
                        .sameSite("Lax")
                        .secure(requestSecure)
                        .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
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
