package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

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
class EmailOutboxPersistenceTest {
    @Autowired EmailOutboxRepository repository;
    @Autowired EmailOutboxService service;
    @Autowired LeadRepository leads;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    @Autowired com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository settings;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach void clear() {
        repository.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
        if (settings.findByWorkspaceId(workspace.getId()).isEmpty()) {
            settings.saveAndFlush(com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                    .createNeutral(workspace, (byte) 1));
        }
    }
    @AfterEach void cleanUp() { repository.deleteAll(); }

    @Test
    void deduplicatesNormalizesClaimsInOrderAndCompletesOnce() {
        Instant now = Instant.now().minusSeconds(10);
        Lead lead = leads.saveAndFlush(Lead.create(workspace, "Test Lead", "intent@example.invalid", null,
                null, "Consulting", null, null, null,
                "A sufficiently detailed inquiry message.", "test"));
        EmailOutbox later = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "  SECOND@EXAMPLE.INVALID ", lead, "event:second", now.plusSeconds(1));
        EmailOutbox first = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                " First@Example.Invalid ", lead, "event:first", now);
        EmailOutbox duplicate = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "first@example.invalid", lead, "event:first", now.plusSeconds(2));

        assertThat(repository.count()).isEqualTo(2);
        assertThat(duplicate.getId()).isEqualTo(first.getId());
        assertThat(first.getRecipient()).isEqualTo("first@example.invalid");
        assertThat(first.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(first.getDeliveryCount()).isZero();

        var claims = service.claimAvailable();
        assertThat(claims).extracting(ClaimedEmail::outboxId)
                .containsExactly(first.getId(), later.getId());
        assertThat(claims).allSatisfy(claim -> {
            assertThat(claim.deliveryCount()).isOne();
            assertThat(claim.leaseToken()).isNotBlank();
        });
        ClaimedEmail claim = claims.getFirst();
        assertThat(service.markDelivered(new ClaimedEmail(claim.outboxId(), "wrong",
                claim.templateType(), claim.recipient(), claim.deliveryCount()))).isFalse();
        assertThat(service.markDelivered(claim)).isTrue();
        assertThat(service.markDelivered(claim)).isFalse();
        assertThat(repository.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.DELIVERED);
    }

    @Test
    void deletingOptionalLeadPreservesDeliveryAudit() {
        Lead lead = leads.saveAndFlush(Lead.create(workspace, "Test Lead", "lead@example.invalid", null,
                null, "Consulting", null, null, null,
                "A sufficiently detailed inquiry message.", "test"));
        EmailOutbox outbox = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", lead, "event:lead-delete", Instant.now());

        leads.deleteById(lead.getId());

        EmailOutbox retained = repository.findById(outbox.getId()).orElseThrow();
        assertThat(retained.getLead()).isNull();
    }

    @Test
    void inactiveWorkspaceRowsAreHeldWithoutBlockingActiveWork() {
        var suspended = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "suspended-email-" + UUID.randomUUID(), "Suspended Email",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.SUSPENDED));
        var pending = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "pending-email-" + UUID.randomUUID(), "Pending Email",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.PENDING));
        Lead suspendedLead = leads.saveAndFlush(Lead.create(suspended, "Suspended", "suspended@example.invalid",
                null, null, "Consulting", null, null, null,
                "A sufficiently detailed suspended inquiry message.", "test"));
        Lead pendingLead = leads.saveAndFlush(Lead.create(pending, "Pending", "pending@example.invalid",
                null, null, "Consulting", null, null, null,
                "A sufficiently detailed pending inquiry message.", "test"));
        Lead activeLead = leads.saveAndFlush(Lead.create(workspace, "Active", "active@example.invalid",
                null, null, "Consulting", null, null, null,
                "A sufficiently detailed active inquiry message.", "test"));
        EmailOutbox suspendedEmail = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "suspended-recipient@example.invalid", suspendedLead, "event:suspended", Instant.now().minusSeconds(3));
        EmailOutbox pendingEmail = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "pending-recipient@example.invalid", pendingLead, "event:pending", Instant.now().minusSeconds(2));
        EmailOutbox activeEmail = service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "active-recipient@example.invalid", activeLead, "event:active", Instant.now().minusSeconds(1));

        assertThat(service.claimAvailable()).extracting(ClaimedEmail::outboxId)
                .containsExactly(activeEmail.getId());
        assertThat(repository.findById(suspendedEmail.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(repository.findById(pendingEmail.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(repository.findById(suspendedEmail.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(repository.findById(pendingEmail.getId()).orElseThrow().getStatus())
                .isEqualTo(EmailOutboxStatus.PENDING);
    }
}
