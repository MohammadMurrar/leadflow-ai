package com.mohammadmurrar.leadflow.notification.api;

import com.mohammadmurrar.leadflow.notification.*;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String message,
        UUID leadId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(),
                notification.getSeverity(), notification.getTitle(), notification.getMessage(),
                notification.getLead() == null ? null : notification.getLead().getId(),
                notification.isRead(), notification.getReadAt(), notification.getCreatedAt());
    }
}

