package com.mohammadmurrar.leadflow.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "users", uniqueConstraints =
        @UniqueConstraint(name = "uk_users_normalized_email", columnNames = "normalized_email"))
public class User {
    private static final Pattern TRUSTED_PASSWORD_HASH = Pattern.compile(
            "^\\{argon2@SpringSecurity_v5_8}\\$argon2id\\$v=19\\$m=16384,t=2,p=1"
                    + "\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}$");
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

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {}

    public static User createAdministrator(Workspace workspace, String email, String displayName,
            String passwordHash) {
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
        user.workspace = java.util.Objects.requireNonNull(workspace, "Workspace is required");
        return user;
    }

    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required");
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void recordSuccessfulLogin() { lastLoginAt = Instant.now(); }

    public boolean changePasswordHash(String encodedPasswordHash) {
        if (encodedPasswordHash == null || encodedPasswordHash.isBlank()
                || encodedPasswordHash.length() > 255
                || !TRUSTED_PASSWORD_HASH.matcher(encodedPasswordHash).matches()) {
            throw new IllegalArgumentException("Encoded password hash is invalid");
        }
        // Reusing the same encoded hash is an explicit no-op: no false audit/version change.
        if (encodedPasswordHash.equals(passwordHash)) return false;
        passwordHash = encodedPasswordHash;
        return true;
    }

    @PrePersist
    void onCreate() { createdAt = updatedAt = Instant.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getEmail() { return email; }
    public String getNormalizedEmail() { return normalizedEmail; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public Workspace getWorkspace() { return workspace; }
    public Instant getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() { return "User[redacted]"; }
}
