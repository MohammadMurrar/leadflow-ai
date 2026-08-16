package com.mohammadmurrar.leadflow.settings.api;

import jakarta.validation.constraints.*;

public record UpdateWorkspaceSettingsRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(max = 120) String workspaceName,
        String contactEmail,
        String description) {
}
