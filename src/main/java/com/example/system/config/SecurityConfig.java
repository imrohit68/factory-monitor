package com.example.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DashboardSessionInvalidateFilter dashboardSessionInvalidateFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/workstations/audio-preview", "/admin/workstations/audio-preview/**")
                        .permitAll()
                        .requestMatchers("/admin/**", "/report/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .addFilterAfter(dashboardSessionInvalidateFilter, SecurityContextHolderFilter.class)
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/admin", false)
                        .permitAll())
                .logout(logout -> logout
                        // Same cache-bust as manual "Return to Dashboard" links — fresh HTML + local /vendor/* scripts.
                        .logoutSuccessHandler(
                                (HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) -> {
                                    String ctx = request.getContextPath();
                                    String url = (ctx == null ? "" : ctx) + "/?cb=" + System.currentTimeMillis();
                                    response.sendRedirect(response.encodeRedirectURL(url));
                                })
                        .permitAll())
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/admin/workstations/audio-preview",
                        "/admin/workstations/audio-preview/**",
                        "/api/dashboard/playback-sync",
                        "/api/dashboard/playback-sync/clear",
                        "/api/dashboard/playback-sync/ended",
                        "/api/dashboard/simulation/toggle/**"));
        return http.build();
    }
}
