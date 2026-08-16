package com.mohammadmurrar.leadflow.lead.api;

import com.mohammadmurrar.leadflow.lead.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;

public record LeadResponse(
        UUID id,
        long version,
        String fullName,
        String email,
        String phone,
        String company,
        String requestedService,
        BigDecimal estimatedBudget,
        LocalDate desiredStartDate,
        String message,
        String source,
        LeadStatus status,
        LeadPriority priority,
        Integer qualificationScore,
        String category,
        String aiSummary,
        String recommendedReply,
        Instant createdAt,
        Instant updatedAt
) {
    public static LeadResponse from(Lead lead) {
        return new LeadResponse(lead.getId(), lead.getVersion(), lead.getFullName(), lead.getEmail(), lead.getPhone(),
                lead.getCompany(), lead.getRequestedService(), lead.getEstimatedBudget(),
                lead.getDesiredStartDate(), lead.getMessage(), lead.getSource(), lead.getStatus(),
                lead.getPriority(), lead.getQualificationScore(), lead.getCategory(),
                lead.getAiSummary(), lead.getRecommendedReply(), lead.getCreatedAt(), lead.getUpdatedAt());
    }
}
