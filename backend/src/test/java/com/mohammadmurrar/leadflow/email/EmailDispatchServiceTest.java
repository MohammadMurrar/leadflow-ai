package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.passwordreset.PasswordResetEmailDeliveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import org.mockito.InOrder;

class EmailDispatchServiceTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("unavailableResetScenarios")
    void resetEligibilityFailureNeverContactsSmtpAndDoesNotBlockLaterRow(
            String scenario, RuntimeException failure, EmailFailureCode expectedCode) throws Exception {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailDeliveryProperties properties = propertiesWithMaximumAttempts(3);
        UUID requestId = UUID.randomUUID();
        var resetRequest = mock(com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest.class);
        when(resetRequest.getId()).thenReturn(requestId);
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        var resetUser = mock(com.mohammadmurrar.leadflow.user.User.class);
        when(resetRequest.getWorkspace()).thenReturn(workspace);
        when(resetRequest.getUser()).thenReturn(resetUser);
        when(resetUser.getWorkspace()).thenReturn(workspace);
        EmailOutbox rejected = EmailOutbox.create(EmailTemplateType.PASSWORD_RESET,
                "recipient@example.invalid", null, "password-reset:rejected", java.time.Instant.now().minusSeconds(1));
        setField(rejected, "passwordResetRequest", resetRequest);
        setField(rejected, "workspace", workspace);
        EmailOutbox later = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "later@example.invalid", ownedLead(),
                "new-lead:later", java.time.Instant.now().minusSeconds(1));
        when(repository.findClaimableForUpdate(any(), anyInt())).thenReturn(List.of(rejected, later));
        when(repository.findEligibleByIdAndWorkspaceId(eq(rejected.getId()), any())).thenReturn(Optional.of(rejected));
        when(repository.findEligibleByIdAndWorkspaceId(eq(later.getId()), any())).thenReturn(Optional.of(later));
        EmailOutboxService outbox = new EmailOutboxService(repository, insertDao, properties,
                EmailTestSupport.usdSettingsRepository());
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        RenderedEmail laterEmail = new RenderedEmail("Lead", "Fixed");
        when(reset.prepare(eq(requestId), any(), any())).thenThrow(failure);
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(laterEmail);
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                properties, reset);

        assertThat(service.dispatchAvailable()).as(scenario).isEqualTo(2);
        verify(reset).prepare(eq(requestId), any(), any());
        verify(sender, never()).send(eq(rejected.getRecipient()), any());
        verify(sender).send(later.getRecipient(), laterEmail);
        assertThat(rejected.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(rejected.getFailureCode()).isEqualTo(expectedCode);
        assertThat(rejected.getDeliveryCount()).isOne();
        assertThat(later.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
        assertThat(later.getDeliveryCount()).isOne();
    }

    private static Stream<Arguments> unavailableResetScenarios() {
        return Stream.of(
                Arguments.of("referenced request missing", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("consumed request", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("superseded request", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("expired request", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("missing nonce", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("missing key version", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("confirmation hash mismatch", unavailable(), EmailFailureCode.REJECTED),
                Arguments.of("unknown historical key version",
                        new IllegalStateException("safe test seam"), EmailFailureCode.UNEXPECTED),
                Arguments.of("invalid public URL configuration",
                        new IllegalStateException("safe test seam"), EmailFailureCode.UNEXPECTED));
    }

    private static RuntimeException unavailable() {
        return new PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException();
    }

    @Test
    void missingResetAssociationUsesFixedFailureAndDoesNotBlockLaterRow() {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailDeliveryProperties properties = propertiesWithMaximumAttempts(3);
        EmailOutbox malformed = EmailOutbox.create(EmailTemplateType.PASSWORD_RESET,
                "recipient@example.invalid", null, "password-reset:missing", java.time.Instant.now().minusSeconds(1));
        setWorkspace(malformed);
        EmailOutbox later = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "later@example.invalid", ownedLead(),
                "new-lead:later", java.time.Instant.now().minusSeconds(1));
        when(repository.findClaimableForUpdate(any(), anyInt())).thenReturn(List.of(malformed, later));
        when(repository.findEligibleByIdAndWorkspaceId(eq(malformed.getId()), any())).thenReturn(Optional.of(malformed));
        when(repository.findEligibleByIdAndWorkspaceId(eq(later.getId()), any())).thenReturn(Optional.of(later));
        EmailOutboxService outbox = new EmailOutboxService(repository, insertDao, properties,
                EmailTestSupport.usdSettingsRepository());
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(new RenderedEmail("Lead", "Fixed"));
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                properties, reset);

        assertThat(service.dispatchAvailable()).isEqualTo(1);
        assertThat(malformed.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(malformed.getFailureCode()).isNull();
        assertThat(malformed.getDeliveryCount()).isZero();
        assertThat(later.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
        verify(sender, times(1)).send(later.getRecipient(), new RenderedEmail("Lead", "Fixed"));
        verifyNoInteractions(reset);
    }

    @Test
    void passwordResetIsPreparedBeforeSmtpAndCompletedOnlyAfterOutboxDelivery() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        UUID requestId = UUID.randomUUID();
        ClaimedEmail claim = new ClaimedEmail(UUID.randomUUID(), "lease",
                EmailTemplateType.PASSWORD_RESET, "recipient@example.invalid", 1,
                null, null, null, null, null, requestId);
        RenderedEmail rendered = new RenderedEmail("Reset password", "Fixed reset body");
        when(outbox.claimAvailable()).thenReturn(List.of(claim));
        when(outbox.renewLeaseForDelivery(claim)).thenReturn(true);
        when(reset.prepare(eq(requestId), any(), any())).thenReturn(rendered);
        when(outbox.markDelivered(claim)).thenReturn(true);
        when(reset.complete(eq(requestId), any(), any())).thenReturn(true);
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"), reset);

        assertThat(service.dispatchAvailable()).isOne();

        InOrder order = inOrder(outbox, reset, sender);
        order.verify(outbox).renewLeaseForDelivery(claim);
        order.verify(reset).prepare(eq(requestId), any(), any());
        order.verify(sender).send(claim.recipient(), rendered);
        order.verify(outbox).markDelivered(claim);
        order.verify(reset).complete(eq(requestId), any(), any());
        verifyNoInteractions(renderer);
        verify(outbox, never()).markFailed(eq(claim), any());
    }

    @Test
    void undeliverableResetUsesSafeFailurePathAndDoesNotContactSmtp() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        UUID requestId = UUID.randomUUID();
        ClaimedEmail claim = new ClaimedEmail(UUID.randomUUID(), "lease",
                EmailTemplateType.PASSWORD_RESET, "recipient@example.invalid", 1,
                null, null, null, null, null, requestId);
        when(outbox.claimAvailable()).thenReturn(List.of(claim));
        when(outbox.renewLeaseForDelivery(claim)).thenReturn(true);
        when(reset.prepare(eq(requestId), any(), any())).thenThrow(
                new PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException());
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"), reset);

        assertThat(service.dispatchAvailable()).isOne();
        verify(outbox).markFailed(claim, EmailFailureCode.REJECTED);
        verifyNoInteractions(sender, renderer);
        verify(outbox, never()).markDelivered(any());
    }

    @Test
    void acceptedResetCompletionFailureNeverReschedulesOrBlocksLaterRows() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        UUID requestId = UUID.randomUUID();
        ClaimedEmail resetClaim = new ClaimedEmail(UUID.randomUUID(), "reset-lease",
                EmailTemplateType.PASSWORD_RESET, "recipient@example.invalid", 1,
                null, null, null, null, null, requestId);
        ClaimedEmail later = claim(EmailTemplateType.NEW_INQUIRY, "later-lease");
        when(outbox.claimAvailable()).thenReturn(List.of(resetClaim, later), List.of());
        when(outbox.renewLeaseForDelivery(any())).thenReturn(true);
        when(reset.prepare(eq(requestId), any(), any())).thenReturn(new RenderedEmail("Reset", "Fixed"));
        when(renderer.render(later)).thenReturn(new RenderedEmail("Lead", "Fixed"));
        when(outbox.markDelivered(any())).thenReturn(true);
        when(reset.complete(eq(requestId), any(), any())).thenThrow(new IllegalStateException("sentinel"));
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"), reset);

        assertThat(service.dispatchAvailable()).isEqualTo(2);
        assertThat(service.dispatchAvailable()).isZero();
        verify(sender, times(2)).send(anyString(), any());
        verify(outbox).markDelivered(resetClaim);
        verify(outbox).markDelivered(later);
        verify(outbox, never()).markFailed(eq(resetClaim), any());
        verify(reset, times(1)).complete(eq(requestId), any(), any());
    }

    @Test
    void disabledDispatcherDoesNothing() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(false, ""));

        assertThat(service.dispatchAvailable()).isZero();
        verifyNoInteractions(outbox, renderer, sender);
    }

    @Test
    void sendsEachClaimOnceAndRecordsOnlyTheMatchingOutcome() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        var first = claim(EmailTemplateType.NEW_INQUIRY, "lease-one");
        var second = claim(EmailTemplateType.QUALIFICATION_COMPLETED, "lease-two");
        var third = claim(EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION, "lease-three");
        when(outbox.claimAvailable()).thenReturn(List.of(first, second, third));
        when(outbox.renewLeaseForDelivery(any())).thenReturn(true);
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(new RenderedEmail("Fixed subject", "Fixed body"));
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).doThrow(new EmailDeliveryException(EmailFailureCode.CONNECTION))
                .doThrow(new IllegalStateException("unsafe provider detail"))
                .when(sender).send(anyString(), any());

        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"));

        assertThat(service.dispatchAvailable()).isEqualTo(3);
        InOrder order = inOrder(outbox, renderer, sender);
        order.verify(outbox).renewLeaseForDelivery(first);
        order.verify(renderer).render(first);
        order.verify(sender).send(eq(first.recipient()), any(RenderedEmail.class));
        order.verify(outbox).renewLeaseForDelivery(second);
        verify(sender, times(3)).send(anyString(), any(RenderedEmail.class));
        verify(outbox).markDelivered(first);
        verify(outbox).markFailed(second, EmailFailureCode.CONNECTION);
        verify(outbox).markFailed(third, EmailFailureCode.UNEXPECTED);
        verify(outbox, never()).enqueue(any(), any(), any(), any(), any());
    }

    @Test
    void acceptedMessagesAreNeverRescheduledWhenCompletionFails() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        ClaimedEmail exception = claim(EmailTemplateType.NEW_INQUIRY, "lease-one");
        ClaimedEmail rejected = claim(EmailTemplateType.NEW_INQUIRY, "lease-two");
        ClaimedEmail later = claim(EmailTemplateType.NEW_INQUIRY, "lease-three");
        when(outbox.claimAvailable()).thenReturn(List.of(exception, rejected, later));
        when(outbox.renewLeaseForDelivery(any())).thenReturn(true);
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(new RenderedEmail("Fixed subject", "Fixed body"));
        when(outbox.markDelivered(exception)).thenThrow(new IllegalStateException("database detail"));
        when(outbox.markDelivered(rejected)).thenReturn(false);
        when(outbox.markDelivered(later)).thenReturn(true);

        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"));

        assertThat(service.dispatchAvailable()).isEqualTo(3);
        verify(sender, times(3)).send(anyString(), any());
        verify(outbox, never()).markFailed(eq(exception), any());
        verify(outbox, never()).markFailed(eq(rejected), any());
        verify(outbox).markDelivered(later);
    }

    @Test
    void lifecycleFailureAndStaleLeaseDoNotStopLaterClaimsOrSendStaleEmail() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        ClaimedEmail stale = claim(EmailTemplateType.NEW_INQUIRY, "stale-lease");
        ClaimedEmail failure = claim(EmailTemplateType.NEW_INQUIRY, "active-lease");
        ClaimedEmail later = claim(EmailTemplateType.NEW_INQUIRY, "later-lease");
        when(outbox.claimAvailable()).thenReturn(List.of(stale, failure, later));
        when(outbox.renewLeaseForDelivery(stale)).thenReturn(false);
        when(outbox.renewLeaseForDelivery(failure)).thenThrow(new IllegalStateException("sensitive database detail"));
        when(outbox.renewLeaseForDelivery(later)).thenReturn(true);
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(new RenderedEmail("Fixed subject", "Fixed body"));
        when(outbox.markDelivered(later)).thenReturn(true);

        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"));

        assertThat(service.dispatchAvailable()).isEqualTo(3);
        verify(sender, times(1)).send(anyString(), any());
        verify(outbox, never()).markDelivered(stale);
        verify(outbox, never()).markFailed(eq(failure), any());
        verify(outbox).markDelivered(later);
    }

    @Test
    void resetFailureAtMaximumAttemptExhaustsWithoutSmtpAndLaterRowContinues() throws Exception {
        EmailOutboxRepository repository = mock(EmailOutboxRepository.class);
        EmailOutboxInsertDao insertDao = mock(EmailOutboxInsertDao.class);
        EmailDeliveryProperties properties = propertiesWithMaximumAttempts(3);
        EmailOutbox resetOutbox = EmailOutbox.create(EmailTemplateType.PASSWORD_RESET,
                "recipient@example.invalid", null, "password-reset:max", java.time.Instant.now().minusSeconds(1));
        setWorkspace(resetOutbox);
        setField(resetOutbox, "deliveryCount", 2);
        EmailOutbox laterOutbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "later@example.invalid", ownedLead(),
                "new-lead:later", java.time.Instant.now().minusSeconds(1));
        when(repository.findClaimableForUpdate(any(), anyInt()))
                .thenReturn(List.of(resetOutbox, laterOutbox));
        var resetRequest = mock(com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest.class);
        var resetUser = mock(com.mohammadmurrar.leadflow.user.User.class);
        var workspace = resetOutbox.getWorkspace();
        when(resetRequest.getId()).thenReturn(UUID.randomUUID());
        when(resetRequest.getWorkspace()).thenReturn(workspace);
        when(resetRequest.getUser()).thenReturn(resetUser);
        when(resetUser.getWorkspace()).thenReturn(workspace);
        setField(resetOutbox, "passwordResetRequest", resetRequest);
        when(repository.findEligibleByIdAndWorkspaceId(resetOutbox.getId(), workspace.getId()))
                .thenReturn(Optional.of(resetOutbox));
        when(repository.findEligibleByIdAndWorkspaceId(laterOutbox.getId(), workspace.getId()))
                .thenReturn(Optional.of(laterOutbox));
        EmailOutboxService outbox = new EmailOutboxService(repository, insertDao, properties,
                EmailTestSupport.usdSettingsRepository());
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        EmailSender sender = mock(EmailSender.class);
        PasswordResetEmailDeliveryService reset = mock(PasswordResetEmailDeliveryService.class);
        when(reset.prepare(eq(resetRequest.getId()), eq(workspace.getId()), any()))
                .thenThrow(new PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException());
        when(renderer.render(any(ClaimedEmail.class))).thenReturn(new RenderedEmail("Lead", "Fixed"));
        EmailDispatchService service = new EmailDispatchService(outbox, renderer, sender,
                properties, reset);

        assertThat(service.dispatchAvailable()).isEqualTo(2);
        assertThat(resetOutbox.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(resetOutbox.getDeliveryCount()).isEqualTo(3);
        assertThat(resetOutbox.getFailureCode()).isEqualTo(EmailFailureCode.REJECTED);
        assertThat(laterOutbox.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
        verify(sender, never()).send(eq(resetOutbox.getRecipient()), any());
        verify(sender).send(eq(laterOutbox.getRecipient()), any());
        verify(reset).prepare(eq(resetRequest.getId()), eq(workspace.getId()), any());
    }

    private EmailDeliveryProperties propertiesWithMaximumAttempts(int maximumAttempts) {
        EmailDeliveryProperties base = EmailTemplateRendererTest.properties(
                true, "https://app.example.invalid");
        return new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                base.senderAddress(), base.senderDisplayName(), base.replyTo(), base.publicBaseUrl(),
                base.batchSize(), base.schedulerInterval(), base.leaseDuration(), maximumAttempts,
                base.initialBackoff(), base.maximumBackoff(), base.connectionTimeout(),
                base.readTimeout(), base.writeTimeout());
    }

    private void setField(Object target, String name, Object value)
            throws ReflectiveOperationException {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setWorkspace(EmailOutbox outbox) {
        try {
            setField(outbox, "workspace",
                    com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private com.mohammadmurrar.leadflow.lead.Lead ownedLead() {
        var lead = mock(com.mohammadmurrar.leadflow.lead.Lead.class);
        when(lead.getWorkspace()).thenReturn(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA());
        return lead;
    }

    private ClaimedEmail claim(EmailTemplateType type, String lease) {
        return new ClaimedEmail(UUID.randomUUID(), lease, type,
                "recipient@example.invalid", 1);
    }
}
