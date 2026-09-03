package com.mohammadmurrar.leadflow.email;

import java.util.UUID;
import java.math.BigDecimal;
import com.mohammadmurrar.leadflow.lead.LeadPriority;

public record ClaimedEmail(
        UUID outboxId,
        UUID workspaceId,
        String leaseToken,
        EmailTemplateType templateType,
        String recipient,
        int deliveryCount,
        String leadFullName,
        String company,
        String requestedService,
        Integer qualificationScore,
        LeadPriority priority,
        BigDecimal estimatedBudget,
        String currency,
        UUID passwordResetRequestId) {

    public ClaimedEmail(UUID outboxId, String leaseToken, EmailTemplateType templateType,
            String recipient, int deliveryCount) {
        this(outboxId, null, leaseToken, templateType, recipient, deliveryCount,
                null, null, null, null, null, null, null, null);
    }

    public ClaimedEmail(UUID outboxId, String leaseToken, EmailTemplateType templateType,
            String recipient, int deliveryCount, String leadFullName, String company,
            String requestedService, Integer qualificationScore, LeadPriority priority) {
        this(outboxId, null, leaseToken, templateType, recipient, deliveryCount, leadFullName,
                company, requestedService, qualificationScore, priority, null, null, null);
    }

    public ClaimedEmail(UUID outboxId, String leaseToken, EmailTemplateType templateType,
            String recipient, int deliveryCount, String leadFullName, String company,
            String requestedService, Integer qualificationScore, LeadPriority priority,
            UUID passwordResetRequestId) {
        this(outboxId, null, leaseToken, templateType, recipient, deliveryCount, leadFullName,
                company, requestedService, qualificationScore, priority, null, null, passwordResetRequestId);
    }

    @Override
    public String toString() {
        return "ClaimedEmail[redacted]";
    }
}
