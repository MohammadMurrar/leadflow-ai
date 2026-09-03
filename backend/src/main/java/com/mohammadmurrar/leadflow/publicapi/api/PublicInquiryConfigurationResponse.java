package com.mohammadmurrar.leadflow.publicapi.api;

import java.util.List;

public record PublicInquiryConfigurationResponse(
        String workspaceName,
        String description,
        String publicBrandName,
        String publicTagline,
        String publicLogoPath,
        String currency,
        String responseTimeText,
        String privacyPolicyUrl,
        String privacyNoticeText,
        String privacyNoticeVersion,
        List<PublicServiceResponse> services
) {}
