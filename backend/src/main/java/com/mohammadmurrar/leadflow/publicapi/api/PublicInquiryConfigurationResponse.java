package com.mohammadmurrar.leadflow.publicapi.api;

import java.util.List;

public record PublicInquiryConfigurationResponse(
        String workspaceName,
        String description,
        List<PublicServiceResponse> services
) {}
