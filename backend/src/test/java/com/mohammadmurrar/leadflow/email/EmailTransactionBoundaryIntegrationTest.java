package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.*;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "leadflow.email-delivery.enabled=true",
        "leadflow.email-delivery.smtp-host=mail.example.invalid",
        "leadflow.email-delivery.smtp-port=587",
        "leadflow.email-delivery.smtp-username=test-user",
        "leadflow.email-delivery.smtp-password=test-password",
        "leadflow.email-delivery.smtp-auth=true",
        "leadflow.email-delivery.smtp-start-tls=true",
        "leadflow.email-delivery.sender-address=sender@example.invalid",
        "leadflow.email-delivery.sender-display-name=LeadFlow",
        "leadflow.email-delivery.public-base-url=https://app.example.invalid",
        "leadflow.email-delivery.batch-size=5",
        "leadflow.email-delivery.scheduler-interval=PT1H",
        "leadflow.email-delivery.lease-duration=PT2M",
        "leadflow.email-delivery.maximum-delivery-attempts=3",
        "leadflow.email-delivery.initial-backoff=PT1S",
        "leadflow.email-delivery.maximum-backoff=PT1M",
        "leadflow.email-delivery.connection-timeout=PT5S",
        "leadflow.email-delivery.read-timeout=PT5S",
        "leadflow.email-delivery.write-timeout=PT5S"
})
@Import(EmailTransactionBoundaryIntegrationTest.SenderConfiguration.class)
class EmailTransactionBoundaryIntegrationTest {
    @Autowired EmailOutboxService outboxService;
    @Autowired EmailOutboxRepository repository;
    @Autowired EmailDispatchService dispatchService;
    @Autowired RecordingSender sender;
    @Autowired TransactionTemplate transactions;
    @Autowired LeadRepository leads;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    @Autowired com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository settings;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach
    void clear() {
        repository.deleteAll();
        leads.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
        if (settings.findByWorkspaceId(workspace.getId()).isEmpty()) {
            settings.saveAndFlush(com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                    .createNeutral(workspace, (byte) 1));
        }
        sender.called.set(false);
    }

    @Test
    void proxyBoundariesCommitClaimBeforeSendingAndRejectOuterTransactions() {
        EmailOutbox outbox = outboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", lead("boundary@example.invalid"),
                "event:transaction-boundary", Instant.now().minusSeconds(1));

        assertThat(dispatchService.dispatchAvailable()).isEqualTo(1);
        assertThat(sender.called).isTrue();
        assertThat(repository.findById(outbox.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.DELIVERED);
        assertThatThrownBy(() -> transactions.executeWithoutResult(
                ignored -> dispatchService.dispatchAvailable()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void concurrentEnqueueAndClaimProduceOneIntentAndOneClaim() throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(2);
        Lead lead = lead("concurrent@example.invalid");
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<EmailOutbox> enqueue = () -> {
                start.await();
                return outboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                        "recipient@example.invalid", lead, "event:concurrent", Instant.now());
            };
            Future<EmailOutbox> first = workers.submit(enqueue);
            Future<EmailOutbox> second = workers.submit(enqueue);
            start.countDown();

            assertThat(first.get().getId()).isEqualTo(second.get().getId());
            assertThat(repository.count()).isEqualTo(1);

            CountDownLatch claimStart = new CountDownLatch(1);
            Callable<List<ClaimedEmail>> claim = () -> {
                claimStart.await();
                return outboxService.claimAvailable();
            };
            Future<List<ClaimedEmail>> firstClaims = workers.submit(claim);
            Future<List<ClaimedEmail>> secondClaims = workers.submit(claim);
            claimStart.countDown();

            List<ClaimedEmail> combined = new ArrayList<>();
            combined.addAll(firstClaims.get());
            combined.addAll(secondClaims.get());
            assertThat(combined).hasSize(1);
            assertThat(combined.getFirst().deliveryCount()).isOne();
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            workers.shutdownNow();
        }
    }

    @Test
    void enqueueJoinsOuterCommitAndRollbackTransactions() {
        UUID committedId = transactions.execute(status -> outboxService.enqueue(
                EmailTemplateType.NEW_INQUIRY, "recipient@example.invalid", lead("commit@example.invalid"),
                "event:outer-commit", Instant.now()).getId());
        assertThat(Boolean.TRUE.equals(transactions.execute(
                status -> repository.existsById(committedId)))).isTrue();

        UUID rolledBackId = transactions.execute(status -> {
            UUID id = outboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                    "recipient@example.invalid", lead("rollback@example.invalid"),
                    "event:outer-rollback", Instant.now()).getId();
            status.setRollbackOnly();
            return id;
        });
        assertThat(Boolean.TRUE.equals(transactions.execute(
                status -> repository.existsById(rolledBackId)))).isFalse();
        assertThat(sender.called).isFalse();
    }

    private Lead lead(String email) {
        return leads.saveAndFlush(Lead.create(workspace, "Test Lead", email, null, null,
                "Consulting", null, null, null,
                "A sufficiently detailed inquiry message.", "test"));
    }

    @TestConfiguration
    static class SenderConfiguration {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    static class RecordingSender implements EmailSender {
        private final AtomicBoolean called = new AtomicBoolean();

        @Override
        public void send(String recipient, RenderedEmail email) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            called.set(true);
        }
    }
}
