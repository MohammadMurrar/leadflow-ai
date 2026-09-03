package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.lead.LeadQualificationWebhookClient;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.qualification.api.QualificationDispatchRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service
public class QualificationDispatchService {
    private final QualificationDispatchOutboxRepository repository;
    private final QualificationAttemptService attemptService;
    private final LeadQualificationWebhookClient webhookClient;
    private final QualificationReliabilityProperties properties;
    private final TransactionTemplate transactions;
    private final String workerId = UUID.randomUUID().toString();

    public QualificationDispatchService(QualificationDispatchOutboxRepository repository,
            QualificationAttemptService attemptService,
            LeadQualificationWebhookClient webhookClient,
            QualificationReliabilityProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.attemptService = attemptService;
        this.webhookClient = webhookClient;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public int dispatchAvailable() {
        if (!properties.dispatcherEnabled()) return 0;
        List<ClaimedDispatch> claimed = transactions.execute(status -> claim());
        if (claimed == null) return 0;
        claimed.forEach(this::deliver);
        return claimed.size();
    }

    private List<ClaimedDispatch> claim() {
        Instant now = Instant.now();
        return repository.findClaimableForUpdate(now, properties.batchSize()).stream().map(outbox -> {
            outbox.claim(workerId, now, now.plus(properties.leaseDuration()));
            QualificationAttempt attempt = outbox.getAttempt();
            return new ClaimedDispatch(outbox.getId(), outbox.getWorkspace().getId(),
                    attempt.getId(), outbox.getDeliveryCount(),
                    new QualificationDispatchRequest(attempt.getLead().getId(), attempt.getId(),
                            attempt.getAttemptNumber(), LeadResponse.from(attempt.getLead())));
        }).toList();
    }

    private void deliver(ClaimedDispatch claimed) {
        try {
            Boolean eligible = transactions.execute(status -> repository
                    .findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId()).isPresent());
            if (!Boolean.TRUE.equals(eligible)) return;
            if (webhookClient.send(claimed.request())) {
                transactions.executeWithoutResult(status -> repository
                        .findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId())
                        .ifPresent(outbox -> outbox.delivered(workerId, Instant.now())));
                return;
            }
        } catch (RuntimeException ignored) {
            // The classified persistent outcome below intentionally omits raw transport details.
        }
        boolean exhausted = claimed.deliveryCount() >= properties.maximumDeliveryAttempts();
        transactions.executeWithoutResult(status -> repository
                .findEligibleByIdAndWorkspaceId(claimed.outboxId(), claimed.workspaceId()).ifPresent(outbox -> {
            if (exhausted) {
                outbox.exhaust(workerId, QualificationFailureCode.WEBHOOK_DELIVERY_FAILED);
            } else {
                outbox.reschedule(workerId, QualificationFailureCode.WEBHOOK_DELIVERY_FAILED,
                        Instant.now().plus(backoff(claimed.deliveryCount())));
            }
        }));
        if (exhausted) attemptService.failKnownDelivery(claimed.attemptId(), claimed.workspaceId());
    }

    private Duration backoff(int deliveryCount) {
        long multiplier = 1L << Math.min(Math.max(deliveryCount - 1, 0), 20);
        Duration candidate;
        try {
            candidate = properties.initialBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException ex) {
            return properties.maximumBackoff();
        }
        return candidate.compareTo(properties.maximumBackoff()) > 0 ? properties.maximumBackoff() : candidate;
    }

    private record ClaimedDispatch(UUID outboxId, UUID workspaceId, UUID attemptId, int deliveryCount,
                                   QualificationDispatchRequest request) {}
}
