package com.mohammadmurrar.leadflow.settings.api;

import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateWorkspaceSettingsRequest(
        @PositiveOrZero long version,
        @NotBlank @Size(max = 120) String workspaceName,
        @Size(max = 180) String contactEmail,
        @Size(max = 500) String description,
        @Size(max = 120) String publicBrandName,
        @Size(max = 240) String publicTagline,
        @Size(max = 500) String publicLogoPath,
        @NotBlank @Size(max = 64) String timeZone,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotBlank @Size(max = 240) String responseTimeText,
        @Size(max = 2048) String privacyPolicyUrl,
        @Size(max = 1000) String privacyNoticeText,
        @Size(max = 64) String privacyNoticeVersion,
        @NotNull @Size(max = 10) List<@NotBlank @Email @Size(max = 254) String> notificationRecipients) {

    public UpdateWorkspaceSettingsRequest {
        workspaceName = trim(workspaceName);
        contactEmail = normalizeOptional(contactEmail);
        description = normalizeOptional(description);
        publicBrandName = normalizeOptional(publicBrandName);
        publicTagline = normalizeOptional(publicTagline);
        publicLogoPath = normalizeOptional(publicLogoPath);
        timeZone = trim(timeZone);
        currency = trim(currency);
        responseTimeText = trim(responseTimeText);
        privacyPolicyUrl = normalizeOptional(privacyPolicyUrl);
        privacyNoticeText = normalizeOptional(privacyNoticeText);
        privacyNoticeVersion = normalizeOptional(privacyNoticeVersion);
        notificationRecipients = notificationRecipients == null ? null
                : notificationRecipients.stream().map(UpdateWorkspaceSettingsRequest::trim).toList();
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String normalizeOptional(String value) {
        String normalized = trim(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
