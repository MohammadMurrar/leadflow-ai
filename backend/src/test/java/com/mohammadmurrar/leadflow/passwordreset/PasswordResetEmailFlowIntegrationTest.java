package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.persistence.EntityManager;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=true",
        "leadflow.password-reset.active-key-version=v1",
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
class PasswordResetEmailFlowIntegrationTest {
    @DynamicPropertySource
    static void resetKey(DynamicPropertyRegistry registry) {
        registry.add("leadflow.password-reset.hmac-keys", () -> "v1=" + "A".repeat(43));
    }

    @Autowired PasswordResetRequestService requestsService;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired PasswordResetTokenService tokens;
    @Autowired EmailOutboxRepository outboxes;
    @MockitoSpyBean EmailOutboxService outboxService;
    @MockitoSpyBean PasswordResetEmailDeliveryService resetDelivery;
    @Autowired EmailDispatchService dispatcher;
    @Autowired UserRepository users;
    @Autowired com.mohammadmurrar.leadflow.lead.LeadRepository leads;
    @Autowired EntityManager entityManager;
    @MockitoBean EmailSender sender;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    @Autowired com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository settings;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach
    void clear() {
        outboxes.deleteAll();
        requests.deleteAll();
        leads.deleteAll();
        users.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
        if (settings.findByWorkspaceId(workspace.getId()).isEmpty()) {
            settings.saveAndFlush(com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                    .createNeutral(workspace, (byte) 1));
        }
    }

    @Test
    void realResetDispatchCommitsClaimOutsideSmtpAndPersistsNoRawToken() {
        User user = users.saveAndFlush(User.createAdministrator(workspace,
                "admin@example.invalid", "Administrator", "hash"));
        Instant now = Instant.now();
        assertThat(requestsService.request("admin@example.invalid", now))
                .isEqualTo(PasswordResetRequestService.Result.CREATED);
        entityManager.clear();
        PasswordResetRequest request = requests.findAll().getFirst();
        EmailOutbox outbox = outboxes.findAll().getFirst();
        Instant persistedCreatedAt = request.getCreatedAt();
        Instant persistedExpiresAt = request.getExpiresAt();
        assertThat(persistedCreatedAt).isEqualTo(now.truncatedTo(ChronoUnit.MICROS));
        assertThat(persistedExpiresAt).isEqualTo(persistedCreatedAt.plus(Duration.ofMinutes(30)));
        assertThat(persistedExpiresAt).isBeforeOrEqualTo(now.plus(Duration.ofMinutes(30)));
        assertThat(persistedCreatedAt.getNano() % 1_000).isZero();
        byte[] nonce = request.getDeliveryNonce();
        byte[] confirmation = request.getTokenHash();
        byte[] raw = null;
        byte[] calculated = null;
        String encoded;
        try (PasswordResetTokenService.SensitiveToken token = tokens.derive(request.getId(),
                request.getUser().getId(), persistedExpiresAt, nonce, request.getDeliveryKeyVersion())) {
            raw = token.bytes();
            encoded = token.encoded();
            calculated = tokens.storedHash(raw);
            assertThat(Arrays.equals(raw, nonce)).as("nonce must not equal raw token").isFalse();
            assertThat(Arrays.equals(raw, confirmation))
                    .as("confirmation hash must not store raw token").isFalse();
            assertThat(Arrays.equals(calculated, confirmation))
                    .as("persisted confirmation hash must match token digest").isTrue();
            assertPersistedTokenAbsence(request, outbox, raw, encoded);

            AtomicBoolean observed = new AtomicBoolean();
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                EmailOutbox claimed = outboxes.findById(outbox.getId()).orElseThrow();
                assertThat(claimed.getStatus()).isEqualTo(EmailOutboxStatus.IN_PROGRESS);
                RenderedEmail email = invocation.getArgument(1);
                assertThat(email.textBody().contains("/reset-password#token=" + encoded))
                        .as("rendered reset message must contain the reconstructed fragment link")
                        .isTrue();
                observed.set(true);
                return null;
            }).when(sender).send(eq("admin@example.invalid"), any(RenderedEmail.class));

            assertThat(dispatcher.dispatchAvailable()).isOne();
            assertThat(observed).isTrue();
        } finally {
            if (raw != null) Arrays.fill(raw, (byte) 0);
            if (calculated != null) Arrays.fill(calculated, (byte) 0);
            Arrays.fill(nonce, (byte) 0);
            Arrays.fill(confirmation, (byte) 0);
        }

        entityManager.clear();
        EmailOutbox delivered = outboxes.findById(outbox.getId()).orElseThrow();
        PasswordResetRequest completed = requests.findById(request.getId()).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
        assertThat(delivered.getDeliveryCount()).isOne();
        assertThat(completed.getEmailDeliveredAt()).isNotNull();
        assertThat(completed.getDeliveryNonce()).isNull();
        assertThat(completed.getDeliveryKeyVersion()).isNull();
        assertThat(completed.getTokenHash()).hasSize(32);
        verify(sender, times(1)).send(anyString(), any(RenderedEmail.class));
    }

    private void assertPersistedTokenAbsence(PasswordResetRequest request, EmailOutbox outbox,
            byte[] rawToken, String encodedToken) {
        List<String> persistedStrings = Arrays.asList(
                request.getDeliveryKeyVersion(), outbox.getRecipient(), outbox.getDeduplicationKey(),
                outbox.getLeaseToken(), outbox.getStatus().name(),
                outbox.getFailureCode() == null ? null : outbox.getFailureCode().name());
        String requestId = request.getId().toString();
        String userId = request.getUser().getId().toString();
        boolean sensitiveStringPresent = persistedStrings.stream()
                .filter(Objects::nonNull)
                .anyMatch(value -> value.contains(encodedToken)
                        || value.contains("reset-password")
                        || value.contains(requestId)
                        || value.contains(userId));
        assertThat(sensitiveStringPresent)
                .as("persisted string fields must contain no token, URL, or textual identity")
                .isFalse();
        assertThat(outbox.getDeduplicationKey().matches("password-reset:[0-9a-f]{64}"))
                .as("reset deduplication key must remain concealed").isTrue();
        assertThat(outbox.getLead()).isNull();
        assertThat(outbox.getPasswordResetRequest().getId()).isEqualTo(request.getId());

        List<byte[]> persistedBinaryFields = List.of(request.getTokenHash(), request.getDeliveryNonce());
        boolean rawBinaryPresent = persistedBinaryFields.stream()
                .anyMatch(value -> Arrays.equals(value, rawToken));
        assertThat(rawBinaryPresent).as("persisted binary fields must contain no raw token").isFalse();
        assertThat(Arrays.stream(EmailOutbox.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .noneMatch(name -> name.toLowerCase(Locale.ROOT).contains("body")
                        || name.toLowerCase(Locale.ROOT).contains("url")))
                .as("outbox must have no persisted body or URL field").isTrue();
    }

    @Test
    void deletingUserAndResetRetainsHistoricalOutboxWithNullAssociation() {
        User user = users.saveAndFlush(User.createAdministrator(workspace,
                "deletion@example.invalid", "Administrator", "hash"));
        requestsService.request("deletion@example.invalid", Instant.now());
        UUID outboxId = outboxes.findAll().getFirst().getId();

        users.deleteById(user.getId());
        users.flush();
        entityManager.clear();

        assertThat(requests.count()).isZero();
        EmailOutbox retained = outboxes.findById(outboxId).orElseThrow();
        assertThat(retained.getTemplateType()).isEqualTo(EmailTemplateType.PASSWORD_RESET);
        assertThat(retained.getPasswordResetRequest()).isNull();
    }

    @Test
    void resetCompletionFailureLeavesRealOutboxDeliveredAndCannotResend() {
        User user = users.saveAndFlush(User.createAdministrator(workspace,
                "completion@example.invalid", "Administrator", "hash"));
        Instant availableAt = Instant.now().minusSeconds(1);
        assertThat(requestsService.request("completion@example.invalid", availableAt))
                .isEqualTo(PasswordResetRequestService.Result.CREATED);
        PasswordResetRequest resetRequest = requests.findAll().getFirst();
        EmailOutbox resetOutbox = outboxes.findAll().getFirst();
        byte[] originalNonce = resetRequest.getDeliveryNonce();
        byte[] originalHash = resetRequest.getTokenHash();

        var lead = leads.saveAndFlush(com.mohammadmurrar.leadflow.lead.Lead.create(
                workspace, "Later Lead", "later@example.invalid", null, null, "Consulting",
                null, null, null,
                "A sufficiently detailed later inquiry message.", "test"));
        EmailOutbox laterOutbox = outboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                "later@example.invalid", lead, "new-lead:post-reset-completion", availableAt);
        doThrow(new IllegalStateException("sensitive completion sentinel"))
                .when(resetDelivery).complete(eq(resetRequest.getId()), any(), any());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(sender).send(anyString(), any(RenderedEmail.class));

        Logger logger = (Logger) LoggerFactory.getLogger(EmailDispatchService.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertThat(dispatcher.dispatchAvailable()).isEqualTo(2);
            entityManager.clear();

            EmailOutbox deliveredReset = outboxes.findById(resetOutbox.getId()).orElseThrow();
            EmailOutbox deliveredLater = outboxes.findById(laterOutbox.getId()).orElseThrow();
            PasswordResetRequest incomplete = requests.findById(resetRequest.getId()).orElseThrow();
            assertThat(deliveredReset.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
            assertThat(deliveredReset.getDeliveryCount()).isOne();
            assertThat(deliveredReset.getStatus()).isNotIn(
                    EmailOutboxStatus.PENDING, EmailOutboxStatus.IN_PROGRESS, EmailOutboxStatus.FAILED);
            assertThat(deliveredLater.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
            assertThat(incomplete.getEmailDeliveredAt()).isNull();
            assertThat(Arrays.equals(incomplete.getDeliveryNonce(), originalNonce))
                    .as("reset nonce must remain available for reconciliation").isTrue();
            assertThat(incomplete.getDeliveryKeyVersion()).isNotNull();
            assertThat(Arrays.equals(incomplete.getTokenHash(), originalHash))
                    .as("confirmation hash must remain unchanged").isTrue();
            verify(outboxService, never()).markFailed(
                    argThat(claim -> claim.outboxId().equals(resetOutbox.getId())), any());

            assertThat(dispatcher.dispatchAvailable()).isZero();
            verify(sender, times(2)).send(anyString(), any(RenderedEmail.class));
            verify(resetDelivery, times(1)).complete(eq(resetRequest.getId()), any(), any());
            assertThat(events.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .isEqualTo("Email dispatch completed with 1 lifecycle persistence failure(s)");
                assertThat(event.getThrowableProxy()).isNull();
            });
            String captured = events.list.toString();
            boolean sensitiveLogContent = List.of("sensitive completion sentinel", "IllegalStateException",
                    "completion@example.invalid", "reset-password", "token=",
                    resetOutbox.getId().toString()).stream().anyMatch(captured::contains);
            assertThat(sensitiveLogContent)
                    .as("dispatch lifecycle log must contain no sensitive completion data").isFalse();
        } finally {
            logger.detachAppender(events);
            events.stop();
            Arrays.fill(originalNonce, (byte) 0);
            Arrays.fill(originalHash, (byte) 0);
        }
    }
}
