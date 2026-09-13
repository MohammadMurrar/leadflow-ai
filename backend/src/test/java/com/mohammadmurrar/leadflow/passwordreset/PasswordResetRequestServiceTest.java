package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetRequestServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void unknownInvalidAndDisabledRequestsAreIndistinguishablySuppressed() {
        Fixture fixture = fixture(true);
        assertThat(fixture.service.request(" missing@example.invalid ", NOW))
                .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
        assertThat(fixture.service.request("not-an-email", NOW))
                .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
        verify(fixture.requests, never()).saveAndFlush(any());
        verifyNoInteractions(fixture.outbox);

        Fixture disabled = fixture(false);
        assertThat(disabled.service.request("admin@example.invalid", NOW))
                .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
        verifyNoInteractions(disabled.users, disabled.requests, disabled.outbox);
    }

    @Test
    void malformedBlankAndOversizedTextIsSuppressedBeforeRepositoryAccess() {
        for (String email : List.of("not-an-email", "   ", "x".repeat(255))) {
            Fixture fixture = fixture(true);
            assertThat(fixture.service.request(email, NOW))
                    .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
            verifyNoInteractions(fixture.users, fixture.requests, fixture.outbox);
        }
    }

    @Test
    void eligibleRequestLocksUserBeforeResetAndCreatesOneAtomicIntent() {
        Fixture fixture = fixture(true);
        User user = administrator();
        when(fixture.users.findByNormalizedEmail("admin@example.invalid")).thenReturn(Optional.of(user));
        when(fixture.users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(fixture.requests.findActiveByUserIdForUpdate(user.getId())).thenReturn(Optional.empty());
        when(fixture.requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(fixture.service.request("  ADMIN@example.invalid ", NOW))
                .isEqualTo(PasswordResetRequestService.Result.CREATED);

        var order = inOrder(fixture.users, fixture.requests, fixture.outbox);
        order.verify(fixture.users).findByNormalizedEmail("admin@example.invalid");
        order.verify(fixture.users).findByIdForUpdate(user.getId());
        order.verify(fixture.requests).findActiveByUserIdForUpdate(user.getId());
        order.verify(fixture.requests).saveAndFlush(any(PasswordResetRequest.class));
        order.verify(fixture.outbox).enqueuePasswordReset(
                eq("admin@example.invalid"), any(PasswordResetRequest.class),
                matches("password-reset:[0-9a-f]{64}"), eq(NOW));
    }

    @Test
    void disabledNonAdminAndChangedEmailCreateNothing() {
        for (int scenario = 0; scenario < 3; scenario++) {
            Fixture fixture = fixture(true);
            User user = mock(User.class);
            UUID id = UUID.randomUUID();
            when(user.getId()).thenReturn(id);
            when(user.getNormalizedEmail()).thenReturn(
                    scenario == 2 ? "changed@example.invalid" : "admin@example.invalid");
            when(user.isEnabled()).thenReturn(scenario != 0);
            when(user.getRole()).thenReturn(scenario == 1 ? null : UserRole.ADMIN);
            when(fixture.users.findByNormalizedEmail("admin@example.invalid"))
                    .thenReturn(Optional.of(user));
            when(fixture.users.findByIdForUpdate(id)).thenReturn(Optional.of(user));

            assertThat(fixture.service.request("admin@example.invalid", NOW))
                    .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
            verify(fixture.requests, never()).findActiveByUserIdForUpdate(any());
            verifyNoInteractions(fixture.outbox);
        }
    }

    @Test
    void enqueueFailureEscapesSoTheSurroundingTransactionCanRollBack() {
        Fixture fixture = fixture(true);
        User user = administrator();
        when(fixture.users.findByNormalizedEmail(anyString())).thenReturn(Optional.of(user));
        when(fixture.users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(fixture.requests.findActiveByUserIdForUpdate(user.getId())).thenReturn(Optional.empty());
        when(fixture.requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.outbox.enqueuePasswordReset(anyString(),
                any(PasswordResetRequest.class), anyString(), eq(NOW)))
                .thenThrow(new EmailOutboxService.EmailOutboxPersistenceException());

        assertThatThrownBy(() -> fixture.service.request("admin@example.invalid", NOW))
                .isInstanceOf(EmailOutboxService.EmailOutboxPersistenceException.class)
                .hasMessage("Email outbox could not be persisted");
    }

    @Test
    void cooldownChangesNothingAndPostCooldownSupersedesThenReplaces() {
        Fixture fixture = fixture(true);
        User user = administrator();
        PasswordResetRequest old = PasswordResetRequest.createActive(UUID.randomUUID(), user,
                bytes(1), bytes(2), "v1", NOW.minusSeconds(60), NOW.plusSeconds(1200));
        when(fixture.users.findByNormalizedEmail(anyString())).thenReturn(Optional.of(user));
        when(fixture.users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(fixture.requests.findActiveByUserIdForUpdate(user.getId())).thenReturn(Optional.of(old));

        assertThat(fixture.service.request("admin@example.invalid", NOW))
                .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
        assertThat(old.getSupersededAt()).isNull();
        verify(fixture.requests, never()).saveAndFlush(any());

        Fixture later = fixture(true);
        when(later.users.findByNormalizedEmail(anyString())).thenReturn(Optional.of(user));
        when(later.users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(later.requests.findActiveByUserIdForUpdate(user.getId())).thenReturn(Optional.of(old));
        when(later.requests.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(later.service.request("admin@example.invalid", NOW.plusSeconds(301)))
                .isEqualTo(PasswordResetRequestService.Result.CREATED);
        assertThat(old.getSupersededAt()).isEqualTo(NOW.plusSeconds(301));
        assertThat(old.getDeliveryNonce()).isNull();
    }

    private Fixture fixture(boolean enabled) {
        UserRepository users = mock(UserRepository.class);
        PasswordResetRequestRepository requests = mock(PasswordResetRequestRepository.class);
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        PasswordResetProperties properties = new PasswordResetProperties(enabled, "v1", "v1=" + key,
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofDays(30));
        PasswordResetTokenService tokens = new PasswordResetTokenService(properties);
        PasswordResetRequestService service = new PasswordResetRequestService(users, requests,
                tokens, properties, outbox, new EmailIntentKeyFactory(),
                new com.mohammadmurrar.leadflow.security.IdentityStateService(mock(jakarta.persistence.EntityManager.class)));
        return new Fixture(users, requests, outbox, service);
    }

    private User administrator() {
        return User.createAdministrator(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "admin@example.invalid", "Administrator", "hash");
    }

    private byte[] bytes(int seed) {
        byte[] value = new byte[32];
        Arrays.fill(value, (byte) seed);
        return value;
    }

    private record Fixture(UserRepository users, PasswordResetRequestRepository requests,
                           EmailOutboxService outbox, PasswordResetRequestService service) {}
}
