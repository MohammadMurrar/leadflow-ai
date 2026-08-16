package com.mohammadmurrar.leadflow.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints =
        @UniqueConstraint(name = "uk_users_normalized_email", columnNames = "normalized_email"))
public class User {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public static User createAdministrator(String email, String displayName, String passwordHash) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.length() > 254) throw new IllegalArgumentException("Email must not exceed 254 characters");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Display name is required");
        String normalizedDisplayName = displayName.trim().replaceAll("\\s+", " ");
        if (normalizedDisplayName.length() > 120) throw new IllegalArgumentException("Display name must not exceed 120 characters");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Password hash is required");
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = email.trim();
        user.normalizedEmail = normalizedEmail;
        user.passwordHash = passwordHash;
        user.displayName = normalizedDisplayName;
        user.role = UserRole.ADMIN;
        user.enabled = true;
        return user;
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void recordSuccessfulLogin() { lastLoginAt = Instant.now(); }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
}
