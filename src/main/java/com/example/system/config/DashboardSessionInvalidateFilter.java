package com.example.system.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends the HTTP session whenever the floor display is opened ({@code GET /} gate or {@code GET
 * /dashboard} matrix). Operators use Login when entering admin; leaving admin via “Return to
 * Dashboard” should not keep an authenticated session on the floor display. After the first visit to
 * {@code /dashboard}, {@link com.example.system.web.DashboardController} sets a long-lived cookie so
 * {@code GET /} can skip the audio gate on later returns without restoring admin auth.
 */
@Component
public class DashboardSessionInvalidateFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isFloorDisplayGet(request)) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        filterChain.doFilter(request, response);
    }

    private static boolean isFloorDisplayGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String ctx = request.getContextPath();
        if (ctx == null) {
            ctx = "";
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        int semi = uri.indexOf(';');
        if (semi >= 0) {
            uri = uri.substring(0, semi);
        }
        return uri.equals(ctx + "/")
                || uri.equals(ctx)
                || uri.equals(ctx + "/dashboard");
    }
}
