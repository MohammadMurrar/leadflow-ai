package com.mohammadmurrar.leadflow.notification;

import com.mohammadmurrar.leadflow.lead.Lead;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_unread_created", columnList = "read_at,created_at"),
        @Index(name = "idx_notifications_created", columnList = "created_at"),
        @Index(name = "idx_notifications_lead_created", columnList = "lead_id,created_at")
})
public class Notification {
    @Id
    private UUID id;

    @Version
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSeverity severity;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    private Instant readAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {}

    public static Notification create(NotificationType type, NotificationSeverity severity,
                                      String title, String message, Lead lead) {
        Notification notification = new Notification();
        notification.id = UUID.randomUUID();
        notification.type = Objects.requireNonNull(type, "Notification type is required");
        notification.severity = Objects.requireNonNull(severity, "Notification severity is required");
        notification.title = requireText(title, "Notification title", 160);
        notification.message = requireText(message, "Notification message", 500);
        notification.lead = lead;
        return notification;
    }

    public void markAsRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }

    public boolean isRead() {
        return readAt != null;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    public UUID getId() { return id; }
    public NotificationType getType() { return type; }
    public NotificationSeverity getSeverity() { return severity; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Lead getLead() { return lead; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
}

