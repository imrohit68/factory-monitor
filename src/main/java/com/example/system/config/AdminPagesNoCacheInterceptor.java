package com.example.system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Prevents browsers and embedded WebViews from serving cached admin/report HTML after sign-out.
 * Without this, a previously viewed "Configure device" page can reappear without a server round-trip,
 * so Spring Security never runs and no login is shown.
 */
public final class AdminPagesNoCacheInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        return true;
    }
}
