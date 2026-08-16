package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.api.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

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

    public QualificationAttemptService(LeadRepository leadRepository,
            QualificationAttemptRepository attemptRepository,
            QualificationDispatchOutboxRepository outboxRepository,
            NotificationService notificationService,
            QualificationReliabilityProperties properties) {
        this.leadRepository = leadRepository;
        this.attemptRepository = attemptRepository;
        this.outboxRepository = outboxRepository;
        this.notificationService = notificationService;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public QualificationAttempt createInitialAttempt(Lead lead) {
        return createAttempt(lead, 1);
    }

    @Transactional
    public QualificationStartResponse start(UUID leadId, UUID attemptId, QualificationStartRequest request) {
        Lead lead = lockedLead(leadId);
        QualificationAttempt attempt = lockedAttempt(lead, attemptId);
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
        Lead lead = lockedLead(leadId);
        QualificationAttempt attempt = lockedAttempt(lead, attemptId);
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
        return outcome(lead, attempt);
    }

    @Transactional
    public QualificationOutcomeResponse fail(UUID leadId, UUID attemptId, QualificationFailureRequest request) {
        Lead lead = lockedLead(leadId);
        QualificationAttempt attempt = lockedAttempt(lead, attemptId);
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
        return outcome(lead, attempt);
    }

    @Transactional
    public QualificationOutcomeResponse retry(UUID leadId, long version) {
        if (!properties.retryEnabled()) throw new ConflictException("Qualification retry is not enabled");
        Lead lead = lockedLead(leadId);
        if (lead.getVersion() != version) throw new ConflictException("Lead changed elsewhere. Refresh and try again");
        if (lead.getStatus() != LeadStatus.AUTOMATION_FAILED) {
            throw new ConflictException("Only a failed automation can be retried");
        }
        if (attemptRepository.findByLeadIdAndStatusIn(leadId, ACTIVE).isPresent()) {
            throw new ConflictException("A qualification attempt is already active");
        }
        int number = attemptRepository.findFirstByLeadIdOrderByAttemptNumberDesc(leadId)
                .map(existing -> existing.getAttemptNumber() + 1).orElse(1);
        lead.retryQualification();
        QualificationAttempt attempt = createAttempt(lead, number);
        return outcome(lead, attempt);
    }

    public List<QualificationAttemptResponse> history(UUID leadId) {
        if (!leadRepository.existsById(leadId)) throw new NotFoundException("Lead not found: " + leadId);
        return attemptRepository.findByLeadIdOrderByAttemptNumberDesc(leadId).stream()
                .map(QualificationAttemptResponse::from).toList();
    }

    @Transactional
    public boolean failKnownDelivery(UUID attemptId) {
        QualificationAttempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus().isTerminal()) return false;
        Lead lead = lockedLead(attempt.getLead().getId());
        if (lead.getStatus() != LeadStatus.QUALIFYING) return false;
        attempt.fail(QualificationFailureCode.WEBHOOK_DELIVERY_FAILED,
                "Automated qualification did not complete.", null, Instant.now());
        lead.markAutomationFailed();
        notificationService.createAutomationFailedNotification(lead);
        return true;
    }

    @Transactional
    public boolean timeOut(UUID attemptId) {
        QualificationAttempt attempt = attemptRepository.findByIdForUpdate(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus().isTerminal()) return false;
        Lead lead = lockedLead(attempt.getLead().getId());
        if (lead.getStatus() != LeadStatus.QUALIFYING) return false;
        attempt.timeOut(Instant.now());
        lead.markAutomationFailed();
        notificationService.createAutomationFailedNotification(lead);
        return true;
    }

    private QualificationAttempt createAttempt(Lead lead, int number) {
        QualificationAttempt attempt = attemptRepository.save(QualificationAttempt.create(lead, number));
        outboxRepository.save(QualificationDispatchOutbox.create(attempt, Instant.now()));
        return attempt;
    }

    private Lead lockedLead(UUID id) {
        return leadRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Lead not found: " + id));
    }

    private QualificationAttempt lockedAttempt(Lead lead, UUID id) {
        QualificationAttempt attempt = attemptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Qualification attempt not found: " + id));
        if (!attempt.getLead().getId().equals(lead.getId())) {
            throw new NotFoundException("Qualification attempt not found: " + id);
        }
        return attempt;
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
