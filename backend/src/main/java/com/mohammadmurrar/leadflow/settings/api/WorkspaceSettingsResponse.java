package com.mohammadmurrar.leadflow.settings.api;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import java.time.Instant;

public record WorkspaceSettingsResponse(
        long version,
        String workspaceName,
        String contactEmail,
        String description,
        Instant updatedAt) {
    public static WorkspaceSettingsResponse from(WorkspaceSettings settings) {
        return new WorkspaceSettingsResponse(settings.getVersion(), settings.getWorkspaceName(),
                settings.getContactEmail(), settings.getDescription(), settings.getUpdatedAt());
    }
}
