package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.email.EmailIntentService;
import com.mohammadmurrar.leadflow.qualification.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

@Service
@Transactional(readOnly = true)
public class QualificationAttemptService {
    private static final List<QualificationAttemptStatus> ACTIVE =
            List.of(QualificationAttemptStatus.PENDING, QualificationAttemptStatus.PROCESSING);
    private final LeadRepository leadRepository;
    private final QualificationAttemptRepository attemptRepository;
    private final QualificationDispatchOutboxRepository outboxRepository;
    private final NotificationService notificationService;
    private final QualificationReliabilityProperties properties;
    private final EmailIntentService emailIntentService;
    private final CurrentWorkspace currentWorkspace;

    public QualificationAttemptService(LeadRepository leadRepository,
            QualificationAttemptRepository attemptRepository,
            QualificationDispatchOutboxRepository outboxRepository,
            NotificationService notificationService,
            QualificationReliabilityProperties properties,
            EmailIntentService emailIntentService,
            CurrentWorkspace currentWorkspace) {
        this.leadRepository = leadRepository;
        this.attemptRepository = attemptRepository;
        this.outboxRepository = outboxRepository;
        this.notificationService = notificationService;
        this.properties = properties;
        this.emailIntentService = emailIntentService;
        this.currentWorkspace = currentWorkspace;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QualificationAttempt createInitialAttempt(Lead lead) {
        return createAttempt(lead, 1);
    }

    @Transactional
    public QualificationStartResponse start(UUID leadId, UUID attemptId, QualificationStartRequest request) {
        QualificationAttempt attempt = lockedActiveAttempt(attemptId);
        Lead lead = lockedCallbackLead(leadId, attempt);
        if (attempt.getStatus() == QualificationAttemptStatus.PROCESSING) {
            return new QualificationStartResponse(attemptId, attempt.getStatus(), false);
        }
        if (attempt.getStatus().isTerminal() || lead.getStatus() != LeadStatus.QUALIFYING) {
            return new QualificationStartResponse(attemptId, attempt.getStatus(), false);
        }
        attempt.start(request.workflowExecutionId(), Instant.now());
        return new QualificationStartResponse(attemptId, attempt.getStatus(), true);
    }

    @Transactional
    public QualificationOutcomeResponse succeed(UUID leadId, UUID attemptId, QualificationSuccessRequest request) {
        QualificationAttempt attempt = lockedActiveAttempt(attemptId);
        Lead lead = lockedCallbackLead(leadId, attempt);
        byte[] fingerprint = fingerprint("SUCCESS", request.score(), request.priority(), request.category().trim(),
                request.summary().trim(), request.recommendedReply().trim(), request.workflowExecutionId().trim());
        if (attempt.getStatus().isTerminal()) {
            if (attempt.matchesTerminal(QualificationAttemptStatus.SUCCEEDED, fingerprint)) return outcome(lead, attempt);
            throw new ConflictException("Qualification attempt has a different terminal outcome");
        }
        requireProcessing(lead, attempt);
        lead.applyQualification(request.score(), request.priority(), request.category(), request.summary(),
                request.recommendedReply());
        attempt.succeed(fingerprint, Instant.now());
        notificationService.createQualificationNotification(lead);
        emailIntentService.enqueueQualificationSuccess(lead, attempt);
        return outcome(lead, attempt);
    }

    @Transactional
    public QualificationOutcomeResponse fail(UUID leadId, UUID attemptId, QualificationFailureRequest request) {
        QualificationAttempt attempt = lockedActiveAttempt(attemptId);
        Lead lead = lockedCallbackLead(leadId, attempt);
        String safeMessage = "Automated qualification did not complete.";
        byte[] fingerprint = fingerprint("FAILURE", request.failureCode(), safeMessage,
                request.workflowExecutionId().trim());
        if (attempt.getStatus().isTerminal()) {
            if (attempt.matchesTerminal(QualificationAttemptStatus.FAILED, fingerprint)) return outcome(lead, attempt);
            throw new ConflictException("Qualification attempt has a different terminal outcome");
        }
        requireProcessing(lead, attempt);
        attempt.fail(request.failureCode(), safeMessage, fingerprint, Instant.now());
        lead.markAutomationFailed();
        notificationService.createAutomationFailedNotification(lead);
        emailIntentService.enqueueQualificationFailure(lead, attempt);
        return outcome(lead, attempt);
    }

    @Transactional
    public QualificationOutcomeResponse retry(UUID leadId, long version) {
        if (!properties.retryEnabled()) throw new ConflictException("Qualification retry is not enabled");
        UUID workspaceId = currentWorkspace.requireActiveId();
        Lead lead = lockedAdminLead(leadId, workspaceId);
        requireConsistentAttemptOwnership(leadId, workspaceId);
        if (lead.getVersion() != version) throw new ConflictException("Lead changed elsewhere. Refresh and try again");
        if (lead.getStatus() != LeadStatus.AUTOMATION_FAILED) {
            throw new ConflictException("Only a failed automation can be retried");
        }
        if (attemptRepository.findActiveByLeadAndWorkspace(leadId, workspaceId, ACTIVE).isPresent()) {
            throw new ConflictException("A qualification attempt is already active");
        }
        int number = attemptRepository.findLatestByLeadAndWorkspace(leadId, workspaceId)
                .map(existing -> existing.getAttemptNumber() + 1).orElse(1);
        lead.retryQualification();
        QualificationAttempt attempt = createAttempt(lead, number);
        return outcome(lead, attempt);
    }

    public List<QualificationAttemptResponse> history(UUID leadId) {
        UUID workspaceId = currentWorkspace.requireActiveId();
        if (!leadRepository.existsByIdAndWorkspaceId(leadId, workspaceId)) {
            throw new NotFoundException("Lead not found");
        }
        requireConsistentAttemptOwnership(leadId, workspaceId);
        return attemptRepository.findHistoryByLeadAndWorkspace(leadId, workspaceId).stream()
                .map(QualificationAttemptResponse::from).toList();
    }

    @Transactional
    public boolean failKnownDelivery(UUID attemptId, UUID workspaceId) {
        QualificationAttempt attempt = attemptRepository
                .findActiveByIdAndWorkspaceIdForUpdate(attemptId, workspaceId).orElse(null);
        if (attempt == null || attempt.getStatus().isTerminal()) return false;
        Lead lead = lockedWorkerLead(attempt);
        if (lead == null || !outboxRepository.existsConsistentByAttemptIdAndWorkspaceId(
                attemptId, workspaceId)) return false;
        if (lead.getStatus() != LeadStatus.QUALIFYING) return false;
        attempt.fail(QualificationFailureCode.WEBHOOK_DELIVERY_FAILED,
                "Automated qualification did not complete.", null, Instant.now());
        lead.markAutomationFailed();
        notificationService.createAutomationFailedNotification(lead);
        emailIntentService.enqueueQualificationFailure(lead, attempt);
        return true;
    }

    @Transactional
    public boolean timeOut(UUID attemptId) {
        QualificationAttempt attempt = attemptRepository.findActiveByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus().isTerminal()) return false;
        Lead lead = lockedWorkerLead(attempt);
        if (lead == null || !outboxRepository.existsConsistentByAttemptIdAndWorkspaceId(
                attemptId, attempt.getWorkspace().getId())) return false;
        if (lead.getStatus() != LeadStatus.QUALIFYING) return false;
        attempt.timeOut(Instant.now());
        lead.markAutomationFailed();
        notificationService.createAutomationFailedNotification(lead);
        emailIntentService.enqueueQualificationFailure(lead, attempt);
        return true;
    }

    private QualificationAttempt createAttempt(Lead lead, int number) {
        QualificationAttempt attempt = attemptRepository.save(QualificationAttempt.create(lead, number));
        outboxRepository.save(QualificationDispatchOutbox.create(attempt, Instant.now()));
        return attempt;
    }

    private Lead lockedAdminLead(UUID id, UUID workspaceId) {
        return leadRepository.findByIdAndWorkspaceIdForUpdate(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private void requireConsistentAttemptOwnership(UUID leadId, UUID workspaceId) {
        if (attemptRepository.existsOwnershipMismatchForLead(leadId, workspaceId)) {
            throw new NotFoundException("Lead not found");
        }
    }

    private QualificationAttempt lockedActiveAttempt(UUID id) {
        QualificationAttempt attempt = attemptRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Qualification attempt not found"));
        if (!outboxRepository.existsConsistentByAttemptIdAndWorkspaceId(
                attempt.getId(), attempt.getWorkspace().getId())) {
            throw new NotFoundException("Qualification attempt not found");
        }
        return attempt;
    }

    private Lead lockedCallbackLead(UUID leadId, QualificationAttempt attempt) {
        if (attempt.getWorkspace() == null || attempt.getLead() == null
                || !attempt.getLead().getId().equals(leadId)) {
            throw new NotFoundException("Qualification attempt not found");
        }
        return leadRepository.findByIdAndWorkspaceIdForUpdate(leadId, attempt.getWorkspace().getId())
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private Lead lockedWorkerLead(QualificationAttempt attempt) {
        if (attempt.getWorkspace() == null || attempt.getLead() == null) return null;
        Lead lead = leadRepository.findByIdAndWorkspaceIdForUpdate(
                attempt.getLead().getId(), attempt.getWorkspace().getId()).orElse(null);
        if (lead == null || lead.getWorkspace() == null
                || !lead.getWorkspace().getId().equals(attempt.getWorkspace().getId())) return null;
        return lead;
    }

    private void requireProcessing(Lead lead, QualificationAttempt attempt) {
        if (attempt.getStatus() != QualificationAttemptStatus.PROCESSING || lead.getStatus() != LeadStatus.QUALIFYING) {
            throw new ConflictException("Qualification attempt is not the active processing attempt");
        }
    }

    private QualificationOutcomeResponse outcome(Lead lead, QualificationAttempt attempt) {
        return new QualificationOutcomeResponse(LeadResponse.from(lead), QualificationAttemptResponse.from(attempt));
    }

    private static byte[] fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
