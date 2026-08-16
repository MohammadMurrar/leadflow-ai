package com.mohammadmurrar.leadflow.service;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "services", uniqueConstraints =
        @UniqueConstraint(name = "uk_services_normalized_name", columnNames = "normalized_name"))
public class ServiceOffering {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected ServiceOffering() {}

    public static ServiceOffering create(String name, String description) {
        ServiceOffering offering = new ServiceOffering();
        offering.id = UUID.randomUUID();
        offering.rename(name);
        offering.description = normalizeDescription(description);
        offering.active = true;
        return offering;
    }

    public void update(String name, String description) {
        rename(name);
        this.description = normalizeDescription(description);
    }

    public boolean deactivate() {
        if (!active) return false;
        active = false;
        return true;
    }

    public boolean reactivate() {
        if (active) return false;
        active = true;
        return true;
    }

    private void rename(String value) {
        String normalizedDisplay = normalizeDisplayName(value);
        if (normalizedDisplay.length() > 120) {
            throw new InvalidServiceNameException("Service name must contain 120 characters or fewer after normalization");
        }
        name = normalizedDisplay;
        normalizedName = normalizedDisplay.toLowerCase(Locale.ROOT);
    }

    public static String normalizedKey(String value) {
        return normalizeDisplayName(value).toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidServiceNameException("Service name is required");
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeDescription(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 1000) {
            throw new InvalidServiceDescriptionException("Service description must contain 1000 characters or fewer");
        }
        return normalized;
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
    public String getName() { return name; }
    public String getDescription() { return description; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class InvalidServiceNameException extends RuntimeException {
        public InvalidServiceNameException(String message) { super(message); }
    }

    public static class InvalidServiceDescriptionException extends RuntimeException {
        public InvalidServiceDescriptionException(String message) { super(message); }
    }
}
