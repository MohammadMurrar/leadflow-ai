package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetEmailDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void reconstructsHistoricalKeyTokenAndVerifiesHashBeforeRendering() {
        Fixture fixture = fixture();
        PasswordResetRequest request = fixture.request(NOW.plusSeconds(1800), false);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                request.getId(), request.getWorkspace().getId())).thenReturn(Optional.of(request));
        when(fixture.renderer.renderPasswordReset(anyString()))
                .thenReturn(new RenderedEmail("Reset", "Fixed body"));

        assertThat(fixture.service.prepare(request.getId(), request.getWorkspace().getId(), NOW))
                .isEqualTo(new RenderedEmail("Reset", "Fixed body"));

        verify(fixture.renderer).renderPasswordReset(matches("[A-Za-z0-9_-]{43}"));
    }

    @Test
    void expiredAndHashMismatchNeverRender() {
        Fixture fixture = fixture();
        PasswordResetRequest expired = fixture.request(NOW, false);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                expired.getId(), expired.getWorkspace().getId())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> fixture.service.prepare(
                expired.getId(), expired.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class)
                .hasMessage("Password reset email is unavailable");

        PasswordResetRequest mismatch = fixture.request(NOW.plusSeconds(1800), true);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                mismatch.getId(), mismatch.getWorkspace().getId())).thenReturn(Optional.of(mismatch));
        assertThatThrownBy(() -> fixture.service.prepare(
                mismatch.getId(), mismatch.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class);
        verifyNoInteractions(fixture.renderer);
    }

    @Test
    void missingConsumedAndSupersededRequestsNeverRender() {
        Fixture fixture = fixture();
        UUID missing = UUID.randomUUID();
        UUID workspaceId = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures
                .activeWorkspaceA().getId();
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(missing, workspaceId))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> fixture.service.prepare(missing, workspaceId, NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class);

        PasswordResetRequest consumed = fixture.request(NOW.plusSeconds(1800), false);
        consumed.consume(NOW);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                consumed.getId(), consumed.getWorkspace().getId())).thenReturn(Optional.of(consumed));
        assertThatThrownBy(() -> fixture.service.prepare(
                consumed.getId(), consumed.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class);

        PasswordResetRequest superseded = fixture.request(NOW.plusSeconds(1800), false);
        superseded.supersede(NOW);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                superseded.getId(), superseded.getWorkspace().getId())).thenReturn(Optional.of(superseded));
        assertThatThrownBy(() -> fixture.service.prepare(
                superseded.getId(), superseded.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class);

        verifyNoInteractions(fixture.renderer);
    }

    @ParameterizedTest(name = "missing {0}")
    @ValueSource(strings = {"deliveryNonce", "deliveryKeyVersion"})
    void eachMissingDerivationFieldSeparatelyPreventsRendering(String fieldName) throws Exception {
        Fixture fixture = fixture();
        PasswordResetRequest incomplete = fixture.request(NOW.plusSeconds(1800), false);
        var field = PasswordResetRequest.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(incomplete, null);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                incomplete.getId(), incomplete.getWorkspace().getId())).thenReturn(Optional.of(incomplete));

        assertThatThrownBy(() -> fixture.service.prepare(
                incomplete.getId(), incomplete.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class)
                .hasMessage("Password reset email is unavailable");
        verifyNoInteractions(fixture.renderer);
    }

    @Test
    void unknownHistoricalKeyAndInvalidUrlNeverRenderSuccessfully() {
        Fixture fixture = fixture();
        PasswordResetRequest unknown = fixture.requestWithVersion(
                NOW.plusSeconds(1800), "unknown");
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                unknown.getId(), unknown.getWorkspace().getId())).thenReturn(Optional.of(unknown));
        assertThatThrownBy(() -> fixture.service.prepare(
                unknown.getId(), unknown.getWorkspace().getId(), NOW))
                .isInstanceOf(PasswordResetKeyRing.PasswordResetKeyUnavailableException.class)
                .hasMessage("Password reset key is unavailable");
        verifyNoInteractions(fixture.renderer);

        EmailTemplateRenderer invalidRenderer = new EmailTemplateRenderer(
                emailProperties("http://app.example.invalid"));
        PasswordResetEmailDeliveryService invalid = new PasswordResetEmailDeliveryService(
                fixture.requests, fixture.tokens, invalidRenderer);
        PasswordResetRequest valid = fixture.request(NOW.plusSeconds(1800), false);
        when(fixture.requests.findDeliverableByIdAndWorkspaceId(
                valid.getId(), valid.getWorkspace().getId())).thenReturn(Optional.of(valid));
        assertThatThrownBy(() -> invalid.prepare(valid.getId(), valid.getWorkspace().getId(), NOW))
                .isInstanceOf(EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class)
                .hasMessage("Public application base URL is invalid");
    }

    @Test
    void completionClearsDerivationMaterialButPreservesConfirmationHash() {
        Fixture fixture = fixture();
        PasswordResetRequest request = fixture.request(NOW.plusSeconds(1800), false);
        byte[] confirmation = request.getTokenHash();
        when(fixture.requests.findDeliverableByIdAndWorkspaceIdForUpdate(
                request.getId(), request.getWorkspace().getId())).thenReturn(Optional.of(request));

        assertThat(fixture.service.complete(request.getId(), request.getWorkspace().getId(), NOW)).isTrue();
        assertThat(request.getEmailDeliveredAt()).isEqualTo(NOW);
        assertThat(request.getDeliveryNonce()).isNull();
        assertThat(request.getDeliveryKeyVersion()).isNull();
        assertThat(request.getTokenHash()).isEqualTo(confirmation);
    }

    private Fixture fixture() {
        PasswordResetRequestRepository requests = mock(PasswordResetRequestRepository.class);
        EmailTemplateRenderer renderer = mock(EmailTemplateRenderer.class);
        byte[] keyBytes = new byte[32];
        Arrays.fill(keyBytes, (byte) 7);
        String key = Base64.getUrlEncoder().withoutPadding().encodeToString(keyBytes);
        PasswordResetProperties properties = new PasswordResetProperties(true, "old", "old=" + key,
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofDays(30));
        PasswordResetTokenService tokens = new PasswordResetTokenService(properties);
        return new Fixture(requests, renderer, tokens,
                new PasswordResetEmailDeliveryService(requests, tokens, renderer));
    }

    private EmailDeliveryProperties emailProperties(String baseUrl) {
        return new EmailDeliveryProperties(true, "mail.example.invalid", 587, "test-user",
                "test-password", true, true, "sender@example.invalid", "LeadFlow", null,
                baseUrl, 10, Duration.ofHours(1), Duration.ofMinutes(2), 3,
                Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofSeconds(5),
                Duration.ofSeconds(6), Duration.ofSeconds(7));
    }

    private record Fixture(PasswordResetRequestRepository requests, EmailTemplateRenderer renderer,
                           PasswordResetTokenService tokens,
                           PasswordResetEmailDeliveryService service) {
        PasswordResetRequest request(Instant expiresAt, boolean mismatch) {
            User user = User.createAdministrator(
                    com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                    "admin@example.invalid", "Administrator", "hash");
            UUID id = UUID.randomUUID();
            byte[] nonce = new byte[32];
            Arrays.fill(nonce, (byte) 3);
            byte[] hash;
            try (PasswordResetTokenService.SensitiveToken token = tokens.derive(
                    id, user.getId(), expiresAt, nonce, "old")) {
                byte[] bytes = token.bytes();
                hash = tokens.storedHash(bytes);
                Arrays.fill(bytes, (byte) 0);
            }
            if (mismatch) hash[0] ^= 1;
            return PasswordResetRequest.createActive(id, user, hash, nonce, "old",
                    NOW.minusSeconds(1), expiresAt);
        }

        PasswordResetRequest requestWithVersion(Instant expiresAt, String version) {
            PasswordResetRequest request = request(expiresAt, false);
            try {
                var field = PasswordResetRequest.class.getDeclaredField("deliveryKeyVersion");
                field.setAccessible(true);
                field.set(request, version);
                return request;
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
