package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest;
import com.mohammadmurrar.leadflow.settings.SupportedCurrency;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class EmailOutboxService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern DEDUPLICATION_KEY = Pattern.compile(
            "^[a-z][a-z0-9-]{1,39}:[A-Za-z0-9][A-Za-z0-9._:-]{0,138}$");
    private final EmailOutboxRepository repository;
    private final EmailOutboxInsertDao insertDao;
    private final EmailDeliveryProperties properties;
    private final WorkspaceSettingsRepository settingsRepository;

    public EmailOutboxService(EmailOutboxRepository repository, EmailOutboxInsertDao insertDao,
            EmailDeliveryProperties properties, WorkspaceSettingsRepository settingsRepository) {
        this.repository = repository;
        this.insertDao = insertDao;
        this.properties = properties;
        this.settingsRepository = settingsRepository;
    }

    @Transactional
    public EmailOutbox enqueue(EmailTemplateType templateType, String recipient, Lead lead,
            String deduplicationKey, Instant now) {
        return enqueueValidated(templateType, recipient, lead, null, deduplicationKey, now);
    }

    @Transactional
    public EmailOutbox enqueuePasswordReset(String recipient, PasswordResetRequest resetRequest,
            String deduplicationKey, Instant now) {
        return enqueueValidated(EmailTemplateType.PASSWORD_RESET, recipient, null,
                resetRequest, deduplicationKey, now);
    }

    private EmailOutbox enqueueValidated(EmailTemplateType templateType, String recipient, Lead lead,
            PasswordResetRequest resetRequest, String deduplicationKey, Instant now) {
        String normalizedRecipient = normalizeRecipient(recipient);
        String normalizedKey = normalizeDeduplicationKey(deduplicationKey);
        EmailTemplateType requiredType = Objects.requireNonNull(templateType);
        validateAssociations(requiredType, lead, resetRequest);
        UUID leadId = lead == null ? null : lead.getId();
        UUID resetRequestId = resetRequest == null ? null : resetRequest.getId();
        var workspace = lead == null ? resetRequest.getWorkspace() : lead.getWorkspace();
        if (workspace == null) throw new InvalidEmailOutboxAssociationException();
        if (lead != null && (lead.getWorkspace() == null
                || !workspace.getId().equals(lead.getWorkspace().getId()))) {
            throw new InvalidEmailOutboxAssociationException();
        }
        if (resetRequest != null && (resetRequest.getUser() == null
                || resetRequest.getUser().getWorkspace() == null
                || !workspace.getId().equals(resetRequest.getUser().getWorkspace().getId()))) {
            throw new InvalidEmailOutboxAssociationException();
        }
        Instant requestedAt = Objects.requireNonNull(now);
        boolean inserted = resetRequestId == null
                ? insertDao.insertIfAbsent(UUID.randomUUID(), requiredType.name(),
                        normalizedRecipient, leadId, workspace.getId(), normalizedKey, requestedAt)
                : insertDao.insertIfAbsent(UUID.randomUUID(), requiredType.name(),
                        normalizedRecipient, leadId, resetRequestId, workspace.getId(), normalizedKey, requestedAt);
        EmailOutbox outbox = repository.findByDeduplicationKey(normalizedKey)
                .orElseThrow(EmailOutboxPersistenceException::new);
        if (!inserted && !sameIntent(outbox, requiredType, normalizedRecipient, leadId, resetRequestId)) {
            throw new DeduplicationKeyConflictException();
        }
        return outbox;
    }

    private void validateAssociations(EmailTemplateType templateType, Lead lead,
            PasswordResetRequest resetRequest) {
        boolean resetTemplate = templateType == EmailTemplateType.PASSWORD_RESET;
        boolean valid = resetTemplate
                ? lead == null && resetRequest != null
                : lead != null && resetRequest == null;
        if (!valid) throw new InvalidEmailOutboxAssociationException();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedEmail> claimAvailable() {
        if (!properties.enabled()) return List.of();
        Instant now = Instant.now();
        return repository.findClaimableForUpdate(now, properties.batchSize()).stream()
                .map(outbox -> {
                    if (outbox.getWorkspace() == null
                            || !consistentAssociation(outbox, outbox.getWorkspace().getId())) return null;
                    Lead lead = outbox.getLead();
                    String currency = lead == null ? null : settingsRepository
                            .findByWorkspaceId(outbox.getWorkspace().getId())
                            .map(settings -> settings.getCurrency())
                            .filter(SupportedCurrency::contains)
                            .orElse(null);
                    if (lead != null && currency == null) return null;
                    if (outbox.exhaustWithoutClaimIfAttemptsReached(
                            properties.maximumDeliveryAttempts(), now)) return null;
                    String token = UUID.randomUUID().toString();
                    if (!outbox.claim(token, now, now.plus(properties.leaseDuration()))) return null;
                    return new ClaimedEmail(outbox.getId(), outbox.getWorkspace().getId(), token,
                            outbox.getTemplateType(),
                            outbox.getRecipient(), outbox.getDeliveryCount(),
                            lead == null ? null : lead.getFullName(),
                            lead == null ? null : lead.getCompany(),
                            lead == null ? null : lead.getRequestedService(),
                            lead == null ? null : lead.getQualificationScore(),
                            lead == null ? null : lead.getPriority(),
                            lead == null ? null : lead.getEstimatedBudget(), currency,
                            outbox.getPasswordResetRequest() == null
                                    ? null : outbox.getPasswordResetRequest().getId());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewLeaseForDelivery(ClaimedEmail claimed) {
        Instant now = Instant.now();
        if (claimed.workspaceId() == null) return false;
        return repository.findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId())
                .filter(outbox -> consistentAssociation(outbox, claimed.workspaceId()))
                .map(outbox -> outbox.renewLease(claimed.leaseToken(), now,
                        now.plus(properties.leaseDuration()), properties.maximumDeliveryAttempts()))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markDelivered(ClaimedEmail claimed) {
        if (claimed.workspaceId() == null) return false;
        return repository.findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId())
                .filter(outbox -> consistentAssociation(outbox, claimed.workspaceId()))
                .map(outbox -> outbox.delivered(claimed.leaseToken(), Instant.now()))
                .orElse(false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markFailed(ClaimedEmail claimed, EmailFailureCode code) {
        if (claimed.workspaceId() == null) return false;
        return repository.findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId())
                .filter(outbox -> consistentAssociation(outbox, claimed.workspaceId())).map(outbox -> {
            if (outbox.getDeliveryCount() >= properties.maximumDeliveryAttempts()) {
                return outbox.exhaust(claimed.leaseToken(), code);
            }
            return outbox.reschedule(claimed.leaseToken(), code,
                    Instant.now().plus(backoff(outbox.getDeliveryCount())));
        }).orElse(false);
    }

    private boolean consistentAssociation(EmailOutbox outbox, UUID workspaceId) {
        if (outbox.getWorkspace() == null || !workspaceId.equals(outbox.getWorkspace().getId())) return false;
        if (outbox.getTemplateType() == EmailTemplateType.PASSWORD_RESET) {
            PasswordResetRequest reset = outbox.getPasswordResetRequest();
            return outbox.getLead() == null && reset != null && reset.getWorkspace() != null
                    && reset.getUser() != null && reset.getUser().getWorkspace() != null
                    && workspaceId.equals(reset.getWorkspace().getId())
                    && workspaceId.equals(reset.getUser().getWorkspace().getId());
        }
        Lead lead = outbox.getLead();
        return outbox.getPasswordResetRequest() == null && lead != null && lead.getWorkspace() != null
                && workspaceId.equals(lead.getWorkspace().getId());
    }

    Duration backoff(int deliveryCount) {
        long multiplier = 1L << Math.min(Math.max(deliveryCount - 1, 0), 20);
        // Validated bounds make Duration overflow unreachable: 24 hours * 2^20
        // is far below Duration's maximum, and the result is capped at seven days.
        Duration candidate = properties.initialBackoff().multipliedBy(multiplier);
        return candidate.compareTo(properties.maximumBackoff()) > 0
                ? properties.maximumBackoff() : candidate;
    }

    private String normalizeRecipient(String value) {
        String normalized = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        if (normalized == null || normalized.isEmpty() || normalized.length() > 254
                || !EMAIL.matcher(normalized).matches()) {
            throw new InvalidEmailOutboxRequestException("Email recipient is invalid");
        }
        return normalized;
    }

    private String normalizeDeduplicationKey(String value) {
        // Format: lowercase namespace (2-40 chars), ':', then an event identifier (1-139 chars),
        // with a maximum total length of 180 characters.
        String normalized = value;
        if (normalized == null || normalized.length() > 180
                || !normalized.equals(normalized.trim())
                || !DEDUPLICATION_KEY.matcher(normalized).matches()) {
            throw new InvalidEmailOutboxRequestException("Email deduplication key is invalid");
        }
        return normalized;
    }

    private boolean sameIntent(EmailOutbox outbox, EmailTemplateType type, String recipient,
            UUID leadId, UUID resetRequestId) {
        UUID existingLeadId = outbox.getLead() == null ? null : outbox.getLead().getId();
        UUID existingResetId = outbox.getPasswordResetRequest() == null
                ? null : outbox.getPasswordResetRequest().getId();
        return outbox.getTemplateType() == type
                && outbox.getRecipient().equals(recipient)
                && Objects.equals(existingLeadId, leadId)
                && Objects.equals(existingResetId, resetRequestId);
    }

    public static class InvalidEmailOutboxRequestException extends RuntimeException {
        public InvalidEmailOutboxRequestException(String message) { super(message); }
    }

    public static class DeduplicationKeyConflictException extends RuntimeException {
        public DeduplicationKeyConflictException() {
            super("Email deduplication key is already used by another intent");
        }
    }

    public static class EmailOutboxPersistenceException extends RuntimeException {
        public EmailOutboxPersistenceException() {
            super("Email outbox could not be persisted");
        }
    }

    public static class InvalidEmailOutboxAssociationException extends RuntimeException {
        public InvalidEmailOutboxAssociationException() {
            super("Email outbox association is invalid");
        }
    }
}
