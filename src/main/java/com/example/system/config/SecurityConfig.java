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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**", "/report/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/internal/desktop-heartbeat"))
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
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }
}
