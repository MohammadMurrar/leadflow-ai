package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.springframework.dao.DataAccessResourceFailureException;

class EmailOutboxServiceTest {
    @Test
    void claimsEachInquiryWithItsOwnWorkspaceCurrency() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        var settingsRepository = mock(com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository.class);
        var workspaceA = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        var workspaceB = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceB();
        var leadA = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        var leadB = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        when(leadA.getWorkspace()).thenReturn(workspaceA);
        when(leadB.getWorkspace()).thenReturn(workspaceB);
        var settingsA = mock(com.mohammadmurrar.leadflow.settings.WorkspaceSettings.class);
        var settingsB = mock(com.mohammadmurrar.leadflow.settings.WorkspaceSettings.class);
        when(settingsA.getCurrency()).thenReturn("ILS");
        when(settingsB.getCurrency()).thenReturn("EUR");
        when(settingsRepository.findByWorkspaceId(workspaceA.getId())).thenReturn(Optional.of(settingsA));
        when(settingsRepository.findByWorkspaceId(workspaceB.getId())).thenReturn(Optional.of(settingsB));
        EmailOutbox outboxA = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "a@example.invalid", leadA, "event:a", Instant.now().minusSeconds(1));
        EmailOutbox outboxB = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "b@example.invalid", leadB, "event:b", Instant.now().minusSeconds(1));
        when(repository.findClaimableForUpdate(any(), anyInt())).thenReturn(List.of(outboxA, outboxB));

        var claims = new EmailOutboxService(repository, insertDao, properties(true, 10, 5),
                settingsRepository).claimAvailable();

        assertThat(claims).extracting(ClaimedEmail::workspaceId, ClaimedEmail::currency)
                .containsExactly(tuple(workspaceA.getId(), "ILS"), tuple(workspaceB.getId(), "EUR"));
        verify(settingsRepository).findByWorkspaceId(workspaceA.getId());
        verify(settingsRepository).findByWorkspaceId(workspaceB.getId());
    }

    @Test
    void enforcesTemplateAssociationInvariantsAndResetCollisionIdentity() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());
        var lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        var reset = mock(com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest.class);
        UUID resetId = UUID.randomUUID();
        when(lead.getId()).thenReturn(UUID.randomUUID());
        when(reset.getId()).thenReturn(resetId);
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        when(lead.getWorkspace()).thenReturn(workspace);
        when(reset.getWorkspace()).thenReturn(workspace);
        var resetUser = mock(com.mohammadmurrar.leadflow.user.User.class);
        when(reset.getUser()).thenReturn(resetUser);
        when(resetUser.getWorkspace()).thenReturn(workspace);

        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.PASSWORD_RESET,
                "recipient@example.invalid", lead, "password-reset:key", Instant.now()))
                .isInstanceOf(EmailOutboxService.InvalidEmailOutboxAssociationException.class)
                .hasMessage("Email outbox association is invalid");
        assertThatThrownBy(() -> service.enqueuePasswordReset("recipient@example.invalid",
                null, "password-reset:key", Instant.now()))
                .isInstanceOf(EmailOutboxService.InvalidEmailOutboxAssociationException.class);
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", null, "new-lead:key", Instant.now()))
                .isInstanceOf(EmailOutboxService.InvalidEmailOutboxAssociationException.class);
        verifyNoInteractions(repository);

        EmailOutbox existing = mock(EmailOutbox.class);
        var otherReset = mock(com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest.class);
        when(otherReset.getId()).thenReturn(UUID.randomUUID());
        when(existing.getTemplateType()).thenReturn(EmailTemplateType.PASSWORD_RESET);
        when(existing.getRecipient()).thenReturn("recipient@example.invalid");
        when(existing.getPasswordResetRequest()).thenReturn(otherReset);
        when(insertDao.insertIfAbsent(any(), eq("PASSWORD_RESET"), anyString(), isNull(),
                eq(resetId), eq(workspace.getId()), eq("password-reset:collision"), any())).thenReturn(false);
        when(repository.findByDeduplicationKey("password-reset:collision"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.enqueuePasswordReset("recipient@example.invalid", reset,
                "password-reset:collision", Instant.now()))
                .isInstanceOf(EmailOutboxService.DeduplicationKeyConflictException.class)
                .hasMessage("Email deduplication key is already used by another intent")
                .hasMessageNotContaining("collision")
                .hasMessageNotContaining("recipient")
                .hasMessageNotContaining(resetId.toString());

        org.mockito.Mockito.reset(repository, insertDao, existing);
        when(existing.getTemplateType()).thenReturn(EmailTemplateType.NEW_INQUIRY);
        when(existing.getRecipient()).thenReturn("recipient@example.invalid");
        when(existing.getLead()).thenReturn(lead);
        when(insertDao.insertIfAbsent(any(), eq("PASSWORD_RESET"), anyString(), isNull(),
                eq(resetId), eq(workspace.getId()), eq("password-reset:cross-category"), any())).thenReturn(false);
        when(repository.findByDeduplicationKey("password-reset:cross-category"))
                .thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.enqueuePasswordReset("recipient@example.invalid", reset,
                "password-reset:cross-category", Instant.now()))
                .isInstanceOf(EmailOutboxService.DeduplicationKeyConflictException.class)
                .hasMessage("Email deduplication key is already used by another intent");
    }

    @Test
    void claimHonorsBatchAndMaximumAttemptExhaustsWithoutCreatingRows() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        var properties = properties(true, 2, 1);
        var lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        when(lead.getWorkspace()).thenReturn(workspace);
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", lead, "event:one", Instant.now().minusSeconds(1));
        when(repository.findClaimableForUpdate(any(), eq(2))).thenReturn(List.of(outbox));
        when(repository.findEligibleByIdAndWorkspaceId(outbox.getId(), workspace.getId()))
                .thenReturn(Optional.of(outbox));
        EmailOutboxService service = new EmailOutboxService(repository, insertDao, properties,
                EmailTestSupport.usdSettingsRepository());

        ClaimedEmail claim = service.claimAvailable().getFirst();
        assertThat(claim.deliveryCount()).isOne();
        Instant claimedUntil = outbox.getLockedUntil();
        assertThat(service.renewLeaseForDelivery(claim)).isTrue();
        assertThat(outbox.getLockedUntil()).isAfterOrEqualTo(claimedUntil);
        assertThat(outbox.getDeliveryCount()).isOne();
        assertThat(service.markFailed(claim, EmailFailureCode.REJECTED)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(outbox.getFailureCode()).isEqualTo(EmailFailureCode.REJECTED);
        verify(repository).findClaimableForUpdate(any(), eq(2));
        verify(repository, never()).save(any());
        verifyNoInteractions(insertDao);
    }

    @Test
    void expiredAndReclaimedLeasesCannotRenewWhileCurrentTokenCanComplete() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        var lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        when(lead.getWorkspace()).thenReturn(workspace);
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", lead, "event:lease", Instant.now().minusSeconds(10));
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());
        when(repository.findClaimableForUpdate(any(), anyInt())).thenReturn(List.of(outbox));
        when(repository.findEligibleByIdAndWorkspaceId(outbox.getId(), workspace.getId()))
                .thenReturn(Optional.of(outbox));
        ClaimedEmail first = service.claimAvailable().getFirst();

        assertThat(outbox.renewLease(first.leaseToken(), outbox.getLockedUntil(),
                outbox.getLockedUntil().plusSeconds(10), 5)).isFalse();
        assertThat(outbox.claim("replacement-token", outbox.getLockedUntil(),
                outbox.getLockedUntil().plusSeconds(120))).isTrue();
        assertThat(service.renewLeaseForDelivery(first)).isFalse();
        ClaimedEmail replacement = new ClaimedEmail(outbox.getId(), workspace.getId(),
                "replacement-token", outbox.getTemplateType(), outbox.getRecipient(),
                outbox.getDeliveryCount(), null, null, null, null, null, null, null, null);
        assertThat(service.renewLeaseForDelivery(replacement)).isTrue();
        assertThat(service.markDelivered(replacement)).isTrue();
        assertThat(outbox.getDeliveryCount()).isEqualTo(2);
    }

    @Test
    void duplicateIntentMustMatchAndUnrelatedPersistenceErrorsPropagate() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        com.mohammadmurrar.leadflow.lead.Lead lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        when(lead.getId()).thenReturn(UUID.randomUUID());
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        when(lead.getWorkspace()).thenReturn(workspace);
        EmailOutbox existing = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "first@example.invalid", lead, "event:one", Instant.now());
        when(insertDao.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(repository.findByDeduplicationKey("event:one")).thenReturn(Optional.of(existing));
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());

        assertThat(service.enqueue(EmailTemplateType.NEW_INQUIRY, " FIRST@EXAMPLE.INVALID ",
                lead, "event:one", Instant.now())).isSameAs(existing);
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.QUALIFICATION_COMPLETED,
                "first@example.invalid", lead, "event:one", Instant.now()))
                .isInstanceOf(EmailOutboxService.DeduplicationKeyConflictException.class)
                .hasMessage("Email deduplication key is already used by another intent")
                .hasMessageNotContaining("event:one")
                .hasMessageNotContaining("first@example.invalid");
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "other@example.invalid", lead, "event:one", Instant.now()))
                .isInstanceOf(EmailOutboxService.DeduplicationKeyConflictException.class);
        com.mohammadmurrar.leadflow.lead.Lead differentLead =
                mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        when(differentLead.getId()).thenReturn(UUID.randomUUID());
        when(differentLead.getWorkspace()).thenReturn(workspace);
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "first@example.invalid", differentLead, "event:one", Instant.now()))
                .isInstanceOf(EmailOutboxService.DeduplicationKeyConflictException.class);

        reset(repository, insertDao);
        when(insertDao.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("database detail"));
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "first@example.invalid", lead, "event:two", Instant.now()))
                .isInstanceOf(DataAccessResourceFailureException.class);

        reset(repository, insertDao);
        when(insertDao.insertIfAbsent(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);
        when(repository.findByDeduplicationKey("event:collision")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "first@example.invalid", lead, "event:collision", Instant.now()))
                .isInstanceOf(EmailOutboxService.EmailOutboxPersistenceException.class)
                .hasMessage("Email outbox could not be persisted")
                .hasMessageNotContaining("event:collision")
                .hasMessageNotContaining("first@example.invalid");
    }

    @Test
    void rejectsMalformedNamespacedDeduplicationKeys() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());

        for (String key : List.of("missing-namespace", "UPPER:key", "a:key", "event:bad key",
                "event:", " event:key", "event:key ", "a".repeat(41) + ":key",
                "event:" + "a".repeat(175))) {
            assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                    "recipient@example.invalid", null, key, Instant.now()))
                    .isInstanceOf(EmailOutboxService.InvalidEmailOutboxRequestException.class)
                    .hasMessage("Email deduplication key is invalid")
                    .hasMessageNotContaining(key);
        }
        verifyNoInteractions(repository, insertDao);
    }

    @Test
    void acceptsDocumentedDeduplicationKeyBoundariesAndUuidSuffix() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());
        com.mohammadmurrar.leadflow.lead.Lead lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        when(lead.getId()).thenReturn(UUID.randomUUID());
        when(lead.getWorkspace()).thenReturn(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "test", "Test", com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE));
        for (String key : List.of("ab:x", "a".repeat(40) + ":" + "b".repeat(139),
                "event:550e8400-e29b-41d4-a716-446655440000")) {
            EmailOutbox existing = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                    "recipient@example.invalid", lead, key, Instant.now());
            when(insertDao.insertIfAbsent(any(), any(), any(), any(), any(), eq(key), any())).thenReturn(false);
            when(repository.findByDeduplicationKey(key)).thenReturn(Optional.of(existing));
            assertThat(service.enqueue(EmailTemplateType.NEW_INQUIRY,
                    "recipient@example.invalid", lead, key, Instant.now())).isSameAs(existing);
        }
    }

    @Test
    void backoffIsDeterministicAndBoundedAndRecipientErrorsAreGeneric() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailOutboxService service = new EmailOutboxService(repository, insertDao,
                properties(true, 10, 5), EmailTestSupport.usdSettingsRepository());
        assertThat(service.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(service.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(service.backoff(20)).isEqualTo(Duration.ofMinutes(1));

        EmailDeliveryProperties maximumBounds = maximumBackoffProperties();
        assertThatCode(maximumBounds::validate).doesNotThrowAnyException();
        EmailOutboxService maximumService = new EmailOutboxService(repository, insertDao,
                maximumBounds, EmailTestSupport.usdSettingsRepository());
        Duration highestEffectiveExponent = maximumService.backoff(21);
        assertThat(highestEffectiveExponent).isEqualTo(Duration.ofDays(7))
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThanOrEqualTo(maximumBounds.maximumBackoff());
        assertThat(maximumService.backoff(Integer.MAX_VALUE))
                .isEqualTo(highestEffectiveExponent)
                .isGreaterThanOrEqualTo(Duration.ZERO)
                .isLessThanOrEqualTo(maximumBounds.maximumBackoff());

        assertThatThrownBy(() -> service.enqueue(EmailTemplateType.NEW_INQUIRY,
                "sensitive-invalid-address", null, "event:key", Instant.now()))
                .isInstanceOf(EmailOutboxService.InvalidEmailOutboxRequestException.class)
                .hasMessage("Email recipient is invalid")
                .hasMessageNotContaining("sensitive-invalid-address");
        verifyNoInteractions(repository, insertDao);
    }

    private EmailDeliveryProperties properties(boolean enabled, int batch, int maximumAttempts) {
        EmailDeliveryProperties base = EmailTemplateRendererTest.properties(enabled,
                enabled ? "https://app.example.invalid" : "");
        return new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                base.senderAddress(), base.senderDisplayName(), base.replyTo(), base.publicBaseUrl(),
                batch, base.schedulerInterval(), base.leaseDuration(), maximumAttempts,
                base.initialBackoff(), base.maximumBackoff(), base.connectionTimeout(),
                base.readTimeout(), base.writeTimeout());
    }

    private EmailDeliveryProperties maximumBackoffProperties() {
        EmailDeliveryProperties base = properties(true, 10, 5);
        return new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                base.senderAddress(), base.senderDisplayName(), base.replyTo(), base.publicBaseUrl(),
                base.batchSize(), base.schedulerInterval(), base.leaseDuration(),
                base.maximumDeliveryAttempts(), Duration.ofHours(24), Duration.ofDays(7),
                base.connectionTimeout(), base.readTimeout(), base.writeTimeout());
    }
}
