package com.mohammadmurrar.leadflow.lead.api;

import com.mohammadmurrar.leadflow.lead.LeadStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(
        @NotNull LeadStatus status,
        @NotNull Long version
) {}
