package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.lead.api.LeadResponse;

public record QualificationOutcomeResponse(LeadResponse lead, QualificationAttemptResponse attempt) {
}
