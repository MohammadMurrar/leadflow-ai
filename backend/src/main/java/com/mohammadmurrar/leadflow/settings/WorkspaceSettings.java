package com.mohammadmurrar.leadflow.settings;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "workspace_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_workspace_settings_singleton", columnNames = "singleton_key"))
public class WorkspaceSettings {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "singleton_key", nullable = false)
    private byte singletonKey;

    @Column(name = "workspace_name", nullable = false, length = 120)
    private String workspaceName;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceSettings() {}

    public static WorkspaceSettings createNeutral() {
        WorkspaceSettings settings = new WorkspaceSettings();
        settings.id = UUID.randomUUID();
        settings.singletonKey = 1;
        settings.workspaceName = "My Workspace";
        return settings;
    }

    public boolean update(String workspaceName, String contactEmail, String description) {
        String normalizedName = normalizeName(workspaceName);
        String normalizedEmail = normalizeOptional(contactEmail);
        String normalizedDescription = normalizeOptional(description);
        if (Objects.equals(this.workspaceName, normalizedName)
                && Objects.equals(this.contactEmail, normalizedEmail)
                && Objects.equals(this.description, normalizedDescription)) {
            return false;
        }
        this.workspaceName = normalizedName;
        this.contactEmail = normalizedEmail;
        this.description = normalizedDescription;
        return true;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) throw new InvalidWorkspaceNameException();
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 120) throw new InvalidWorkspaceNameException();
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getWorkspaceName() { return workspaceName; }
    public String getContactEmail() { return contactEmail; }
    public String getDescription() { return description; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class InvalidWorkspaceNameException extends RuntimeException {}
}
