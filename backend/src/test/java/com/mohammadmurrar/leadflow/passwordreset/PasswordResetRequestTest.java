package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PasswordResetRequestTest {
    private static final Instant CREATED = Instant.parse("2030-01-01T00:00:00.123456789Z");

    @Test
    void activeRequestDefensivelyCopiesArraysAndClearsDeliveryMaterial() {
        byte[] hash = bytes(1);
        byte[] nonce = bytes(2);
        PasswordResetRequest request = request(hash, nonce);
        hash[0] = 99;
        nonce[0] = 99;

        assertThat(request.getTokenHash()[0]).isEqualTo((byte) 1);
        assertThat(request.getDeliveryNonce()[0]).isEqualTo((byte) 2);
        assertThat(request.getCreatedAt()).isEqualTo(Instant.parse("2030-01-01T00:00:00.123456Z"));
        assertThat(request.getExpiresAt()).isEqualTo(Instant.parse("2030-01-01T00:30:00.123456Z"));
        assertThat(request.isActiveAt(request.getExpiresAt().minusNanos(1_000))).isTrue();
        assertThat(request.isActiveAt(request.getExpiresAt())).isFalse();
        assertThat(request.clearDeliveryMaterial()).isTrue();
        assertThat(request.clearDeliveryMaterial()).isFalse();
        assertThat(request.getDeliveryNonce()).isNull();
        assertThat(request.getDeliveryKeyVersion()).isNull();
        assertThat(request.getTokenHash()).containsExactly(bytes(1));
        assertThat(request).hasToString("PasswordResetRequest[redacted]");
    }

    @Test
    void consumeAndSupersedeAreTerminalMutuallyExclusiveTransitions() {
        PasswordResetRequest consumed = request(bytes(1), bytes(2));
        assertThat(consumed.consume(CREATED.plusSeconds(10))).isTrue();
        assertThat(consumed.consume(CREATED.plusSeconds(20))).isFalse();
        assertThat(consumed.getActiveSlot()).isNull();
        assertThatThrownBy(() -> consumed.supersede(CREATED.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Password reset request transition is invalid");

        PasswordResetRequest superseded = request(bytes(3), bytes(4));
        assertThat(superseded.supersede(CREATED.plusSeconds(10))).isTrue();
        assertThat(superseded.supersede(CREATED.plusSeconds(20))).isFalse();
        assertThat(superseded.getActiveSlot()).isNull();
        assertThat(superseded.getDeliveryNonce()).isNull();
        assertThatThrownBy(() -> superseded.consume(CREATED.plusSeconds(20)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Password reset request transition is invalid");
        assertThat(superseded.isTerminalBefore(CREATED.plusSeconds(11))).isTrue();
    }

    @Test
    void deliveredTransitionIsIdempotentAndDoesNotInvalidateConfirmationHash() {
        PasswordResetRequest request = request(bytes(5), bytes(6));
        byte[] hash = request.getTokenHash();
        assertThat(request.markEmailDelivered(CREATED.plusSeconds(5))).isTrue();
        assertThat(request.markEmailDelivered(CREATED.plusSeconds(6))).isFalse();
        assertThat(request.getDeliveryNonce()).isNull();
        assertThat(request.getDeliveryKeyVersion()).isNull();
        assertThat(request.getTokenHash()).containsExactly(hash);
        assertThat(request.isActiveAt(CREATED.plusSeconds(10))).isTrue();
    }

    private PasswordResetRequest request(byte[] hash, byte[] nonce) {
        return PasswordResetRequest.createActive(UUID.randomUUID(),
                User.createAdministrator(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                        "admin@example.invalid", "Admin", "{test}encoded"),
                hash, nonce, "v1", CREATED, CREATED.plusSeconds(1800));
    }

    private byte[] bytes(int value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) value);
        return bytes;
    }
}
