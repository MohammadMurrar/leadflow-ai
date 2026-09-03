package com.mohammadmurrar.leadflow.workspace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces", uniqueConstraints =
        @UniqueConstraint(name = "uk_workspaces_public_slug", columnNames = "public_slug"))
public class Workspace {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "public_slug", nullable = false, length = 63)
    private String publicSlug;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Workspace() {}

    public static Workspace create(UUID id, String slug, String displayName, WorkspaceStatus status) {
        Workspace workspace = new Workspace();
        workspace.id = id;
        workspace.publicSlug = slug;
        workspace.displayName = displayName;
        workspace.status = status;
        workspace.createdAt = workspace.updatedAt = Instant.now();
        return workspace;
    }

    public UUID getId() { return id; }
    public String getPublicSlug() { return publicSlug; }
    public String getDisplayName() { return displayName; }
    public WorkspaceStatus getStatus() { return status; }
    public boolean isActive() { return status == WorkspaceStatus.ACTIVE; }
}
