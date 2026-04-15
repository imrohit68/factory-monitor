package com.example.system.web;

import com.example.system.config.ApplicationShutdownService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/shutdown")
@RequiredArgsConstructor
public class AdminShutdownController {

    private final ApplicationShutdownService applicationShutdownService;

    /**
     * Exposes the CSRF token for {@code admin/shutdown.html}. Same constraint as workstation forms:
     * Thymeleaf SpringEL does not expose a non-null {@code #httpServletRequest} for the token.
     */
    @ModelAttribute
    public void shutdownFormCsrf(HttpServletRequest request, Model model) {
        Object attr = request.getAttribute(CsrfToken.class.getName());
        if (attr instanceof CsrfToken token) {
            model.addAttribute("shutdownFormCsrf", token);
        }
    }

    @GetMapping
    public String shutdownPage() {
        return "admin/shutdown";
    }

    @PostMapping
    public String shutdown() {
        applicationShutdownService.shutdownGracefully();
        return "admin/shutdown-ack";
    }
}
