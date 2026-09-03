package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class QualificationWorkerWorkspaceIsolationIntegrationTest {
    @Autowired WorkspaceRepository workspaces;
    @Autowired LeadRepository leads;
    @Autowired QualificationAttemptRepository attempts;
    @Autowired QualificationDispatchOutboxRepository outboxes;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void inactiveOlderJobsAreHeldWithoutBlockingActiveClaimOrConsumingAttempts() {
        Workspace suspended = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "suspended-qualification-" + UUID.randomUUID(), "Suspended Qualification",
                WorkspaceStatus.SUSPENDED));
        Workspace pending = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "pending-qualification-" + UUID.randomUUID(), "Pending Qualification",
                WorkspaceStatus.PENDING));
        Workspace active = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "active-qualification-" + UUID.randomUUID(), "Active Qualification",
                WorkspaceStatus.ACTIVE));
        QualificationDispatchOutbox suspendedOutbox = create(suspended, "suspended-qualification@example.invalid");
        QualificationDispatchOutbox pendingOutbox = create(pending, "pending-qualification@example.invalid");
        QualificationDispatchOutbox activeOutbox = create(active, "active-qualification@example.invalid");

        var claimed = new TransactionTemplate(transactionManager).execute(status ->
                outboxes.findClaimableForUpdate(Instant.now().plusSeconds(1), 10));

        assertThat(claimed).extracting(QualificationDispatchOutbox::getId)
                .containsExactly(activeOutbox.getId());
        assertThat(outboxes.findById(suspendedOutbox.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(outboxes.findById(pendingOutbox.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(outboxes.findById(suspendedOutbox.getId()).orElseThrow().getStatus())
                .isEqualTo(QualificationDispatchStatus.PENDING);
        assertThat(outboxes.findById(pendingOutbox.getId()).orElseThrow().getStatus())
                .isEqualTo(QualificationDispatchStatus.PENDING);

        jdbc.update("update workspaces set status = 'ACTIVE' where id = ?", suspended.getId());
        entityManager.clear();
        var afterReactivation = new TransactionTemplate(transactionManager).execute(status ->
                outboxes.findClaimableForUpdate(Instant.now().plusSeconds(1), 10));
        assertThat(afterReactivation).extracting(QualificationDispatchOutbox::getId)
                .contains(suspendedOutbox.getId());
        assertThat(outboxes.findById(suspendedOutbox.getId()).orElseThrow().getDeliveryCount()).isZero();
    }

    private QualificationDispatchOutbox create(Workspace workspace, String email) {
        Lead lead = Lead.create(workspace, "Worker Isolation", email, null, null, "Consulting",
                null, null, null, "A sufficiently detailed worker isolation inquiry.", "test");
        lead.startQualification();
        lead = leads.saveAndFlush(lead);
        QualificationAttempt attempt = attempts.saveAndFlush(QualificationAttempt.create(lead, 1));
        return outboxes.saveAndFlush(QualificationDispatchOutbox.create(attempt, Instant.now()));
    }
}
