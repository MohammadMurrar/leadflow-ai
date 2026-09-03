package com.mohammadmurrar.leadflow.settings;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "workspace_notification_recipients", uniqueConstraints =
        @UniqueConstraint(name = "uk_workspace_notification_recipient_email",
                columnNames = {"workspace_settings_id", "normalized_email"}))
public class WorkspaceNotificationRecipient {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_settings_id", nullable = false)
    private WorkspaceSettings workspaceSettings;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(name = "normalized_email", nullable = false, length = 254)
    private String normalizedEmail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkspaceNotificationRecipient() {}

    static WorkspaceNotificationRecipient create(WorkspaceSettings settings, String normalizedEmail) {
        WorkspaceNotificationRecipient recipient = new WorkspaceNotificationRecipient();
        recipient.id = UUID.randomUUID();
        recipient.workspaceSettings = Objects.requireNonNull(settings);
        recipient.workspace = Objects.requireNonNull(settings.getWorkspace(), "Workspace is required");
        recipient.normalizedEmail = Objects.requireNonNull(normalizedEmail);
        return recipient;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getNormalizedEmail() { return normalizedEmail; }
    public Workspace getWorkspace() { return workspace; }
}
