package com.example.system.security;

import com.example.system.domain.UserAccount;
import com.example.system.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the initial admin user only when {@code app_user} is empty (first install). After a DB
 * restore, existing rows are left unchanged so recovered credentials keep working.
 */
@Component
@Order(100)
@RequiredArgsConstructor
public class AdminUserSeedRunner implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${system.security.initial-username:admin}")
    private String initialUsername;

    /** Used only when no users exist; ignored when the table already has accounts. */
    @Value("${system.security.initial-password:admin@123}")
    private String initialPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userAccountRepository.count() > 0) {
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(initialUsername);
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        admin.setEnabled(true);
        admin.setRole("ROLE_ADMIN");
        userAccountRepository.save(admin);
    }
}
