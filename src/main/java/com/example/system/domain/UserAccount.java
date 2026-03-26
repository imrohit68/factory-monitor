package com.example.system.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Application login account stored in the database (recovered with DB backup).
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt-encoded password hash. */
    @Column(name = "password_hash", nullable = false, length = 80)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Spring Security authority, e.g. ROLE_ADMIN */
    @Column(nullable = false, length = 32)
    private String role = "ROLE_ADMIN";
}
