package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.passwordreset.PasswordResetEmailDeliveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailDispatchService {
    private static final Logger log = LoggerFactory.getLogger(EmailDispatchService.class);
    private final EmailOutboxService outboxService;
    private final EmailTemplateRenderer renderer;
    private final EmailSender sender;
    private final EmailDeliveryProperties properties;
    private final PasswordResetEmailDeliveryService resetDelivery;

    public EmailDispatchService(EmailOutboxService outboxService, EmailTemplateRenderer renderer,
            EmailSender sender, EmailDeliveryProperties properties) {
        this(outboxService, renderer, sender, properties, null);
    }

    @Autowired
    public EmailDispatchService(EmailOutboxService outboxService, EmailTemplateRenderer renderer,
            EmailSender sender, EmailDeliveryProperties properties,
            PasswordResetEmailDeliveryService resetDelivery) {
        this.outboxService = outboxService;
        this.renderer = renderer;
        this.sender = sender;
        this.properties = properties;
        this.resetDelivery = resetDelivery;
    }

    @Transactional(propagation = Propagation.NEVER)
    public int dispatchAvailable() {
        if (!properties.enabled()) return 0;
        var claimed = outboxService.claimAvailable();
        int lifecycleFailures = 0;
        for (ClaimedEmail email : claimed) {
            if (!deliver(email)) lifecycleFailures++;
        }
        if (lifecycleFailures > 0) {
            log.warn("Email dispatch completed with {} lifecycle persistence failure(s)", lifecycleFailures);
        }
        return claimed.size();
    }

    private boolean deliver(ClaimedEmail claimed) {
        try {
            if (!outboxService.renewLeaseForDelivery(claimed)) return false;
        } catch (RuntimeException ex) {
            return false;
        }
        try {
            // Reset state may become terminal after preparation and before SMTP acceptance.
            // Holding a database lock or transaction across SMTP is intentionally avoided; at
            // worst the recipient receives an already-invalid link. Confirmation remains the
            // authoritative terminal-state check and cannot reactivate or corrupt reset state.
            RenderedEmail rendered = claimed.templateType() == EmailTemplateType.PASSWORD_RESET
                    ? prepareReset(claimed) : renderer.render(claimed);
            sender.send(claimed.recipient(), rendered);
        } catch (EmailDeliveryException ex) {
            return recordFailure(claimed, ex.failureCode());
        } catch (RuntimeException ex) {
            return recordFailure(claimed, EmailFailureCode.UNEXPECTED);
        }

        // A process can theoretically pause longer than any finite lease after renewal. Renewing
        // immediately before SMTP prevents normal batch waiting from consuming the send window.
        // SMTP acceptance followed by failure here leaves the lease for bounded recovery and can
        // rarely duplicate delivery. An accepted message must never be rescheduled by this worker.
        try {
            boolean delivered = outboxService.markDelivered(claimed);
            if (delivered && claimed.passwordResetRequestId() != null) {
                try {
                    if (!resetDelivery.complete(
                            claimed.passwordResetRequestId(), claimed.workspaceId(),
                            java.time.Instant.now())) return false;
                } catch (RuntimeException ignored) {
                    return false;
                }
            }
            return delivered;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private RenderedEmail prepareReset(ClaimedEmail claimed) {
        if (resetDelivery == null || claimed.passwordResetRequestId() == null) {
            throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
        }
        try {
            return resetDelivery.prepare(claimed.passwordResetRequestId(), claimed.workspaceId(),
                    java.time.Instant.now());
        } catch (PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException exception) {
            throw new EmailDeliveryException(EmailFailureCode.REJECTED);
        }
    }

    private boolean recordFailure(ClaimedEmail claimed, EmailFailureCode code) {
        try {
            return outboxService.markFailed(claimed, code);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
