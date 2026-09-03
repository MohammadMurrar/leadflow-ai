package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.qualification.QualificationAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EmailIntentService {
    private static final Logger log = LoggerFactory.getLogger(EmailIntentService.class);
    private static final String NO_INQUIRY_RECIPIENT =
            "Inquiry email notification suppressed because no eligible recipient is configured";
    private final EmailOutboxService outboxService;
    private final InquiryEmailRecipientResolver recipientResolver;
    private final EmailIntentKeyFactory keyFactory;

    public EmailIntentService(EmailOutboxService outboxService,
            InquiryEmailRecipientResolver recipientResolver, EmailIntentKeyFactory keyFactory) {
        this.outboxService = outboxService;
        this.recipientResolver = recipientResolver;
        this.keyFactory = keyFactory;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueNewLead(Lead lead) {
        var workspace = requireWorkspace(lead);
        List<String> recipients = recipientResolver.resolveNewInquiryRecipients(workspace.getId());
        if (recipients.isEmpty()) log.warn(NO_INQUIRY_RECIPIENT);
        enqueue(EmailTemplateType.NEW_INQUIRY, "new-lead", lead.getId(), lead, recipients);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueQualificationSuccess(Lead lead, QualificationAttempt attempt) {
        requireMatchingWorkspace(lead, attempt);
        enqueue(EmailTemplateType.QUALIFICATION_COMPLETED,
                "qualification-success", attempt.getId(), lead,
                recipientResolver.resolveExplicitRecipients(lead.getWorkspace().getId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueueQualificationFailure(Lead lead, QualificationAttempt attempt) {
        requireMatchingWorkspace(lead, attempt);
        enqueue(EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION,
                "qualification-failure", attempt.getId(), lead,
                recipientResolver.resolveExplicitRecipients(lead.getWorkspace().getId()));
    }

    private void enqueue(EmailTemplateType template, String namespace,
            java.util.UUID eventId, Lead lead, List<String> recipients) {
        Instant now = Instant.now();
        for (String recipient : recipients) {
            outboxService.enqueue(template, recipient, lead,
                    keyFactory.key(namespace, eventId, recipient), now);
        }
    }

    private com.mohammadmurrar.leadflow.workspace.Workspace requireWorkspace(Lead lead) {
        if (lead == null || lead.getWorkspace() == null) throw new InvalidEmailIntentAssociationException();
        return lead.getWorkspace();
    }

    private void requireMatchingWorkspace(Lead lead, QualificationAttempt attempt) {
        var workspace = requireWorkspace(lead);
        if (attempt == null || attempt.getWorkspace() == null
                || !workspace.getId().equals(attempt.getWorkspace().getId())
                || attempt.getLead() == null || !lead.getId().equals(attempt.getLead().getId())) {
            throw new InvalidEmailIntentAssociationException();
        }
    }

    public static final class InvalidEmailIntentAssociationException extends RuntimeException {
        public InvalidEmailIntentAssociationException() {
            super("Email intent association is invalid");
        }
    }
}
