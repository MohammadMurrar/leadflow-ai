package com.mohammadmurrar.leadflow.passwordreset;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PasswordResetTokenServiceTest {
    private static final UUID REQUEST = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID USER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant EXPIRY = Instant.ofEpochSecond(1_893_456_000L, 123_456_000);

    @Test
    void matchesFixedHmacVectorAndReconstructsAcrossInstances() {
        byte[] key = sequence(0);
        byte[] nonce = sequence(32);
        PasswordResetTokenService first = service("v1", Map.of("v1", key));
        PasswordResetTokenService restarted = service("v1", Map.of("v1", key));

        try (var token = first.derive(REQUEST, USER, EXPIRY, nonce, "v1");
                var reconstructed = restarted.derive(REQUEST, USER, EXPIRY, nonce, "v1")) {
            assertThat(HexFormat.of().formatHex(token.bytes()))
                    .isEqualTo("b6c2e6706621ad15c9f1ab4f122a44793d1da9da5b0976e0878ce5920ede9b46");
            assertThat(token.encoded()).isEqualTo("tsLmcGYhrRXJ8atPEipEeT0dqdpbCXbgh4zlkg7em0Y")
                    .hasSize(43).doesNotContain("=", "+", "/");
            assertThat(token.bytes()).hasSize(32).isEqualTo(reconstructed.bytes());
        }
    }

    @Test
    void everyBoundMetadataFieldAndKeyAffectsToken() {
        byte[] nonce = sequence(32);
        var service = service("v1", Map.of("v1", sequence(0), "v2", sequence(64)));
        byte[] baseline;
        try (var token = service.derive(REQUEST, USER, EXPIRY, nonce, "v1")) {
            baseline = token.bytes();
        }
        assertDifferent(service, baseline, UUID.randomUUID(), USER, EXPIRY, nonce, "v1");
        assertDifferent(service, baseline, REQUEST, UUID.randomUUID(), EXPIRY, nonce, "v1");
        assertDifferent(service, baseline, REQUEST, USER, EXPIRY.plusNanos(1), nonce, "v1");
        assertDifferent(service, baseline, REQUEST, USER, EXPIRY, sequence(33), "v1");
        assertDifferent(service, baseline, REQUEST, USER, EXPIRY, nonce, "v2");
    }

    @Test
    void strictlyDecodesAndHashesRawTokenBytes() {
        var service = service("v1", Map.of("v1", sequence(0)));
        try (var token = service.derive(REQUEST, USER, EXPIRY, sequence(32), "v1")) {
            byte[] decoded = service.decodeSubmitted(token.encoded());
            byte[] hash = service.storedHash(decoded);
            assertThat(service.matches(hash, decoded)).isTrue();
            assertThat(service.matches(hash, sequence(90))).isFalse();
            byte[] submittedHash = service.decodeSubmitted(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(hash));
            assertThat(service.matches(hash, submittedHash)).isFalse();
        }
        for (String malformed : List.of("", "short", "a".repeat(42), "a".repeat(44),
                "a".repeat(42) + "=", "a".repeat(42) + "+", "a".repeat(42) + "/")) {
            assertThatThrownBy(() -> service.decodeSubmitted(malformed))
                    .isInstanceOf(PasswordResetTokenService.InvalidPasswordResetTokenException.class)
                    .hasMessage("Password reset token is invalid");
        }
    }

    @Test
    void unknownVersionAndRepresentationsAreSafeAndRedacted() {
        var service = service("v1", Map.of("v1", sequence(0)));
        assertThatThrownBy(() -> service.derive(REQUEST, USER, EXPIRY, sequence(32), "missing"))
                .isInstanceOf(PasswordResetKeyRing.PasswordResetKeyUnavailableException.class)
                .hasMessage("Password reset key is unavailable")
                .hasMessageNotContaining("missing");
        try (var token = service.derive(REQUEST, USER, EXPIRY, sequence(32), "v1")) {
            assertThat(service).hasToString("PasswordResetTokenService[redacted]");
            assertThat(token).hasToString("SensitiveToken[redacted]");
            token.clear();
            assertThatThrownBy(token::bytes).hasMessage("Password reset token is cleared");
        }
    }

    private void assertDifferent(PasswordResetTokenService service, byte[] baseline,
            UUID request, UUID user, Instant expiry, byte[] nonce, String version) {
        try (var token = service.derive(request, user, expiry, nonce, version)) {
            assertThat(token.bytes()).isNotEqualTo(baseline);
        }
    }

    private PasswordResetTokenService service(String active, Map<String, byte[]> keys) {
        return new PasswordResetTokenService(PasswordResetKeyRing.enabled(active, keys), new SecureRandom());
    }

    private static byte[] sequence(int offset) {
        byte[] value = new byte[32];
        for (int index = 0; index < value.length; index++) value[index] = (byte) (offset + index);
        return value;
    }
}
