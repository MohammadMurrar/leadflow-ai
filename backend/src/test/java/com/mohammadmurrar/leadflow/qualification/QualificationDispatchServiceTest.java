package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadQualificationWebhookClient;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QualificationDispatchServiceTest {

    @Test
    void unexpectedSuccessfulAcknowledgementReschedulesExistingOutbox() {
        QualificationDispatchOutboxRepository repository = mock(QualificationDispatchOutboxRepository.class);
        QualificationAttemptService attemptService = mock(QualificationAttemptService.class);
        LeadQualificationWebhookClient webhookClient = mock(LeadQualificationWebhookClient.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);

        Lead lead = Lead.create(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "Test Lead", "test@example.com", null, null, "Consulting", null,
                null, null, "A sufficiently detailed test message", "test");
        lead.startQualification();
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        QualificationDispatchOutbox outbox = QualificationDispatchOutbox.create(attempt, Instant.now());
        when(repository.findClaimableForUpdate(any(), eq(1))).thenReturn(List.of(outbox));
        when(repository.findEligibleByIdAndWorkspaceId(
                outbox.getId(), lead.getWorkspace().getId())).thenReturn(Optional.of(outbox));
        when(webhookClient.send(any()))
                .thenThrow(new IllegalStateException("Unexpected webhook acknowledgement status: 200"));

        QualificationDispatchService service = new QualificationDispatchService(repository, attemptService,
                webhookClient, properties(), transactionManager);

        assertThat(service.dispatchAvailable()).isEqualTo(1);
        assertThat(outbox.getStatus()).isEqualTo(QualificationDispatchStatus.PENDING);
        assertThat(outbox.getDeliveryCount()).isEqualTo(1);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getStatus()).isEqualTo(QualificationAttemptStatus.PENDING);
        verify(repository).findClaimableForUpdate(any(), eq(1));
        verify(repository, atLeastOnce()).findEligibleByIdAndWorkspaceId(
                outbox.getId(), lead.getWorkspace().getId());
        verify(repository, never()).save(any());
        verifyNoInteractions(attemptService);
    }

    private QualificationReliabilityProperties properties() {
        return new QualificationReliabilityProperties(true, true, false, Duration.ofHours(1), 1, 5,
                Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofMinutes(5),
                Duration.ofHours(1), Duration.ofHours(1), 10);
    }
}
