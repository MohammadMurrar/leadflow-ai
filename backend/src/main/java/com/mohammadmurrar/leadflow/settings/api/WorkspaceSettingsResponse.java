package com.mohammadmurrar.leadflow.settings.api;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import java.time.Instant;
import java.util.List;

public record WorkspaceSettingsResponse(
        long version,
        String workspaceName,
        String contactEmail,
        String description,
        String publicBrandName,
        String publicTagline,
        String publicLogoPath,
        String timeZone,
        String currency,
        String responseTimeText,
        String privacyPolicyUrl,
        String privacyNoticeText,
        String privacyNoticeVersion,
        List<String> notificationRecipients,
        Instant updatedAt) {
    public static WorkspaceSettingsResponse from(WorkspaceSettings settings) {
        return new WorkspaceSettingsResponse(settings.getVersion(), settings.getWorkspaceName(),
                settings.getContactEmail(), settings.getDescription(), settings.getPublicBrandName(),
                settings.getPublicTagline(), settings.getPublicLogoPath(), settings.getTimeZone(),
                settings.getCurrency(), settings.getResponseTimeText(), settings.getPrivacyPolicyUrl(),
                settings.getPrivacyNoticeText(), settings.getPrivacyNoticeVersion(),
                settings.getNotificationRecipients(), settings.getUpdatedAt());
    }
}
