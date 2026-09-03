package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetConfirmationServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String PASSWORD = "New-password-42!";

    @Test
    void validConfirmationLocksUserThenRequestChangesPasswordConsumesAndInvalidatesSessions() {
        Fixture fixture = fixture();
        fixture.stubActive();

        fixture.service.confirm(fixture.token, PASSWORD, NOW);

        var order = inOrder(fixture.requests, fixture.users, fixture.encoder, fixture.sessions);
        order.verify(fixture.requests).findByTokenHash(any(byte[].class));
        order.verify(fixture.users).findByIdForUpdate(fixture.user.getId());
        order.verify(fixture.requests).findByIdForUpdate(fixture.request.getId());
        order.verify(fixture.encoder).matches(PASSWORD, "{test}old");
        order.verify(fixture.encoder).encode(PASSWORD);
        order.verify(fixture.sessions).deleteByPrincipalName("admin@example.invalid");
        assertThat(fixture.request.getConsumedAt()).isEqualTo(NOW);
        assertThat(fixture.request.getActiveSlot()).isNull();
        assertThat(fixture.user.getPasswordHash()).isEqualTo(fixture.encoded);
    }

    @Test
    void malformedUnknownExpiredConsumedAndSupersededTokensShareGenericOutcome() {
        Fixture malformed = fixture();
        assertGeneric(() -> malformed.service.confirm("not-a-token", PASSWORD, NOW));
        verifyNoInteractions(malformed.requests, malformed.users, malformed.sessions);

        Fixture unknown = fixture();
        when(unknown.requests.findByTokenHash(any())).thenReturn(Optional.empty());
        assertGeneric(() -> unknown.service.confirm(unknown.token, PASSWORD, NOW));

        for (String state : List.of("expired", "consumed", "superseded")) {
            Fixture fixture = fixture();
            fixture.stubActive();
            if (state.equals("expired")) {
                assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD,
                        NOW.plusSeconds(1801)));
            } else if (state.equals("consumed")) {
                fixture.request.consume(NOW.minusSeconds(1));
                assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD, NOW));
            } else {
                fixture.request.supersede(NOW.minusSeconds(1));
                assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD, NOW));
            }
            verify(fixture.sessions, never()).deleteByPrincipalName(anyString());
            verify(fixture.encoder, never()).encode(anyString());
        }
    }

    @Test
    void passwordPolicyAndReuseDoNotConsumeTokenOrDeleteSessions() {
        for (String invalid : List.of("Short1!", " no-leading-space-A1!",
                "alllowercase-42!", "ALLUPPERCASE-42!", "NoDigitsHere!", "NoSymbolHere42",
                "admin-NewPass42!", "admin@example.invalid-A1!")) {
            Fixture fixture = fixture();
            fixture.stubActive();
            assertThatThrownBy(() -> fixture.service.confirm(fixture.token, invalid, NOW))
                    .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordException.class);
            assertThat(fixture.request.getConsumedAt()).isNull();
            verify(fixture.sessions, never()).deleteByPrincipalName(anyString());
        }

        Fixture reused = fixture();
        reused.stubActive();
        when(reused.encoder.matches(PASSWORD, reused.user.getPasswordHash())).thenReturn(true);
        assertThatThrownBy(() -> reused.service.confirm(reused.token, PASSWORD, NOW))
                .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordException.class)
                .hasMessage("Choose a password you have not used before");
        assertThat(reused.request.getConsumedAt()).isNull();
    }

    @Test
    void passwordPolicyUsesUtf16CodeUnitBoundariesAndRejectsWhitespaceAndControls() {
        assertPolicyRejected(validPassword(11));
        assertAccepted(validPassword(12));
        assertAccepted(validPassword(128));
        assertPolicyRejected(validPassword(129));

        assertAccepted("Aa1!" + "x".repeat(6) + "\uD83D\uDE00");
        assertAccepted("Aa1!" + "x".repeat(122) + "\uD83D\uDE00");
        assertPolicyRejected("Aa1!" + "x".repeat(123) + "\uD83D\uDE00");
        assertAccepted("Aa1! internal space");

        for (String invalid : List.of(" " + validPassword(12), validPassword(12) + " ",
                "Aa1!xxxxxxx\t", "Aa1!xxxxxxx\r", "Aa1!xxxxxxx\n",
                "Aa1!xxxxxxx\0", "Aa1!xxxxxxx\u001F")) {
            assertPolicyRejected(invalid);
        }
        assertPolicyRejected("ADMIN@EXAMPLE.INVALID-Aa1!");
        assertPolicyRejected("xxADMINxx-Aa1!zz");
    }

    @Test
    void malformedStoredPasswordHashUsesGenericTokenFailureWithoutMutation() {
        Fixture fixture = fixture();
        fixture.stubActive();
        when(fixture.encoder.matches(PASSWORD, fixture.user.getPasswordHash()))
                .thenThrow(new IllegalArgumentException("sensitive malformed hash"));

        assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD, NOW));

        assertThat(fixture.request.getConsumedAt()).isNull();
        assertThat(fixture.user.getPasswordHash()).isEqualTo("{test}old");
        verify(fixture.encoder, never()).encode(anyString());
        verifyNoInteractions(fixture.sessions);
    }

    @Test
    void disabledAndNonAdminUsersUseGenericTokenFailure() {
        for (int scenario = 0; scenario < 2; scenario++) {
            Fixture fixture = fixture();
            fixture.stubCandidate();
            User user = mock(User.class);
            when(user.getId()).thenReturn(fixture.user.getId());
            when(user.isEnabled()).thenReturn(scenario != 0);
            when(user.getRole()).thenReturn(scenario == 1 ? null : UserRole.ADMIN);
            when(fixture.users.findByIdForUpdate(fixture.user.getId())).thenReturn(Optional.of(user));
            assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD, NOW));
            verify(fixture.requests, never()).findByIdForUpdate(any());
        }
    }

    @Test
    void requestAndUserWorkspaceMismatchUsesGenericFailureWithoutMutation() {
        Fixture fixture = fixture();
        fixture.stubCandidate();
        User otherWorkspaceUser = User.createAdministrator(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceB(),
                "admin@example.invalid", "Admin", "{test}old");
        org.springframework.test.util.ReflectionTestUtils.setField(otherWorkspaceUser, "id", fixture.user.getId());
        when(fixture.users.findByIdForUpdate(fixture.user.getId())).thenReturn(Optional.of(otherWorkspaceUser));
        when(fixture.requests.findByIdForUpdate(fixture.request.getId())).thenReturn(Optional.of(fixture.request));

        assertGeneric(() -> fixture.service.confirm(fixture.token, PASSWORD, NOW));

        assertThat(fixture.request.getConsumedAt()).isNull();
        verify(fixture.encoder, never()).encode(anyString());
        verifyNoInteractions(fixture.sessions);
    }

    private void assertGeneric(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isExactlyInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class)
                .hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE);
    }

    private void assertPolicyRejected(String password) {
        Fixture fixture = fixture();
        fixture.stubActive();
        assertThatThrownBy(() -> fixture.service.confirm(fixture.token, password, NOW))
                .isExactlyInstanceOf(PasswordResetConfirmationService.InvalidPasswordException.class);
        assertThat(fixture.request.getConsumedAt()).isNull();
        verifyNoInteractions(fixture.sessions);
    }

    private void assertAccepted(String password) {
        Fixture fixture = fixture();
        fixture.stubActive();
        when(fixture.encoder.encode(password)).thenReturn(fixture.encoded);
        fixture.service.confirm(fixture.token, password, NOW);
        assertThat(fixture.request.getConsumedAt()).isEqualTo(NOW);
    }

    private String validPassword(int length) {
        return "Aa1!" + "x".repeat(length - 4);
    }

    private Fixture fixture() {
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        PasswordResetProperties properties = new PasswordResetProperties(true, "v1", "v1=" + key,
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofDays(30));
        PasswordResetTokenService tokens = new PasswordResetTokenService(properties);
        User user = User.createAdministrator(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "admin@example.invalid", "Admin", "{test}old");
        UUID requestId = UUID.randomUUID();
        byte[] nonce = tokens.newDeliveryNonce();
        String token;
        byte[] tokenHash;
        try (var sensitive = tokens.derive(requestId, user.getId(), NOW.plusSeconds(1800), nonce, "v1")) {
            token = sensitive.encoded();
            byte[] bytes = sensitive.bytes();
            tokenHash = tokens.storedHash(bytes);
            Arrays.fill(bytes, (byte) 0);
        }
        PasswordResetRequest request = PasswordResetRequest.createActive(requestId, user, tokenHash,
                nonce, "v1", NOW, NOW.plusSeconds(1800));
        PasswordResetRequestRepository requests = mock(PasswordResetRequestRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordResetSessionService sessions = mock(PasswordResetSessionService.class);
        String encoded = "{argon2@SpringSecurity_v5_8}$argon2id$v=19$m=16384,t=2,p=1"
                + "$AAAAAAAAAAAAAAAAAAAAAA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        when(encoder.encode(PASSWORD)).thenReturn(encoded);
        return new Fixture(tokens, requests, users, encoder, sessions, user, request, token,
                encoded, new PasswordResetConfirmationService(tokens, requests, users, encoder,
                        sessions, Clock.fixed(NOW, ZoneOffset.UTC)));
    }

    private record Fixture(PasswordResetTokenService tokens,
            PasswordResetRequestRepository requests, UserRepository users, PasswordEncoder encoder,
            PasswordResetSessionService sessions, User user, PasswordResetRequest request,
            String token, String encoded, PasswordResetConfirmationService service) {
        void stubCandidate() {
            when(requests.findByTokenHash(any())).thenReturn(Optional.of(request));
        }
        void stubActive() {
            stubCandidate();
            when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
            when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        }
    }
}
