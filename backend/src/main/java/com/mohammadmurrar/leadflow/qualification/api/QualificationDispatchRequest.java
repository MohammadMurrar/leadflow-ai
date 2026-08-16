package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import java.util.UUID;

public record QualificationDispatchRequest(
        UUID leadId,
        UUID attemptId,
        int attemptNumber,
        LeadResponse lead) {
}
