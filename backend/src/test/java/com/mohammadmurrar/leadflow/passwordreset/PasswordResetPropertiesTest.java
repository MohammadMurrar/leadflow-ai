package com.mohammadmurrar.leadflow.passwordreset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class PasswordResetPropertiesTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PasswordResetConfiguration.class);

    @Test
    void disabledContextStartsWithoutKeys() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(PasswordResetTokenService.class);
            PasswordResetProperties properties = context.getBean(PasswordResetProperties.class);
            assertThat(properties.tokenLifetime()).isEqualTo(Duration.ofMinutes(30));
            assertThat(properties.requestCooldown()).isEqualTo(Duration.ofMinutes(5));
            assertThat(properties.retention()).isEqualTo(Duration.ofHours(720));
        });
    }

    @Test
    void enabledContextWithoutKeysFailsSafely() {
        contextRunner.withPropertyValues("leadflow.password-reset.enabled=true")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().hasRootCauseInstanceOf(
                                PasswordResetProperties.InvalidPasswordResetConfigurationException.class)
                        .rootCause().hasMessage("Password reset configuration is invalid"));
    }

    @Test
    void disabledModeRequiresNoKeyAndIsRedacted() {
        PasswordResetProperties properties = properties(false, null, null);
        assertThat(properties.validatedKeyRing().enabled()).isFalse();
        assertThat(properties).hasToString("PasswordResetProperties[redacted]");
        assertThat(properties.validatedKeyRing()).hasToString("PasswordResetKeyRing[redacted]");
    }

    @Test
    void enabledModeRejectsMissingMalformedShortDuplicateAndMissingActiveKeys() {
        assertInvalid(properties(true, "v1", ""));
        assertInvalid(properties(true, "v1", "v1=not+url"));
        assertInvalid(properties(true, "v1", "v1=" + encoded(16, 1)));
        assertInvalid(properties(true, "v1", "v1=" + encoded(32, 1) + ",v1=" + encoded(32, 2)));
        assertInvalid(properties(true, "v2", "v1=" + encoded(32, 1)));
        assertInvalid(properties(true, "INVALID", "v1=" + encoded(32, 1)));
    }

    @Test
    void validMultipleVersionsRetainOldKeyForReconstruction() {
        PasswordResetKeyRing ring = properties(true, "v2",
                "v1=" + encoded(32, 1) + ",v2=" + encoded(48, 2)).validatedKeyRing();
        assertThat(ring.enabled()).isTrue();
        assertThat(ring.activeVersion()).isEqualTo("v2");
        assertThat(ring.resolve("v1")).hasSize(32);
        byte[] copy = ring.resolve("v1");
        copy[0] ^= 1;
        assertThat(ring.resolve("v1")[0]).isNotEqualTo(copy[0]);
    }

    @Test
    void keyCountAndDecodedKeyLengthBoundsAreExact() {
        assertThat(properties(true, "v1", "v1=" + encoded(32, 1)).validatedKeyRing().enabled())
                .isTrue();
        assertThat(properties(true, "v1", "v1=" + encoded(64, 1)).validatedKeyRing().enabled())
                .isTrue();
        assertInvalid(properties(true, "v1", "v1=" + encoded(31, 1)));
        assertInvalid(properties(true, "v1", "v1=" + encoded(65, 1)));

        String fourKeys = keyRing(4);
        assertThat(properties(true, "v1", fourKeys).validatedKeyRing().enabled()).isTrue();
        assertInvalid(properties(true, "v1", keyRing(5)));
        assertInvalid(properties(true, "v1", "v1=" + "a".repeat(2045)));
    }

    @Test
    void cooldownAndRetentionBoundsAreExact() {
        assertValidDurations(Duration.ofSeconds(1), Duration.ofDays(1));
        assertValidDurations(Duration.ofMinutes(30), Duration.ofDays(365));
        assertInvalid(properties(true, "v1", "v1=" + encoded(32, 1),
                Duration.ofNanos(999_999_999), Duration.ofDays(30)));
        assertInvalid(properties(true, "v1", "v1=" + encoded(32, 1),
                Duration.ofMinutes(30).plusNanos(1), Duration.ofDays(30)));
        assertInvalid(properties(true, "v1", "v1=" + encoded(32, 1),
                Duration.ofMinutes(5), Duration.ofDays(1).minusNanos(1)));
        assertInvalid(properties(true, "v1", "v1=" + encoded(32, 1),
                Duration.ofMinutes(5), Duration.ofDays(365).plusNanos(1)));
    }

    @Test
    void checkedTimestampArithmeticRejectsOverflowAndAcceptsMaximumDurations() {
        PasswordResetProperties properties = properties(true, "v1", "v1=" + encoded(32, 1),
                Duration.ofMinutes(30), Duration.ofDays(365));
        Instant base = Instant.parse("2030-01-01T00:00:00Z");
        assertThat(properties.tokenExpiresAt(base)).isEqualTo(base.plus(Duration.ofMinutes(30)));
        assertThat(properties.cooldownEndsAt(base)).isEqualTo(base.plus(Duration.ofMinutes(30)));
        assertThat(properties.retentionEndsAt(base)).isEqualTo(base.plus(Duration.ofDays(365)));
        assertThatThrownBy(() -> properties.tokenExpiresAt(Instant.MAX.minusSeconds(1)))
                .isInstanceOf(PasswordResetProperties.InvalidPasswordResetConfigurationException.class)
                .hasMessage("Password reset configuration is invalid");
        assertThatThrownBy(() -> properties.retentionEndsAt(Instant.MAX.minusSeconds(1)))
                .isInstanceOf(PasswordResetProperties.InvalidPasswordResetConfigurationException.class)
                .hasMessage("Password reset configuration is invalid");
    }

    private void assertInvalid(PasswordResetProperties properties) {
        assertThatThrownBy(properties::validatedKeyRing)
                .isInstanceOf(PasswordResetProperties.InvalidPasswordResetConfigurationException.class)
                .hasMessage("Password reset configuration is invalid");
    }

    private PasswordResetProperties properties(boolean enabled, String active, String keys) {
        return new PasswordResetProperties(enabled, active, keys, Duration.ofMinutes(30),
                Duration.ofMinutes(5), Duration.ofDays(30));
    }

    private PasswordResetProperties properties(boolean enabled, String active, String keys,
            Duration cooldown, Duration retention) {
        return new PasswordResetProperties(enabled, active, keys, Duration.ofMinutes(30),
                cooldown, retention);
    }

    private void assertValidDurations(Duration cooldown, Duration retention) {
        assertThat(properties(true, "v1", "v1=" + encoded(32, 1), cooldown, retention)
                .validatedKeyRing().enabled()).isTrue();
    }

    private String keyRing(int count) {
        List<String> entries = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            entries.add("v" + index + "=" + encoded(32, index));
        }
        return String.join(",", entries);
    }

    private String encoded(int length, int seed) {
        byte[] bytes = new byte[length];
        for (int index = 0; index < length; index++) bytes[index] = (byte) (seed + index);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordResetProperties.class)
    @Import(PasswordResetTokenService.class)
    static class PasswordResetConfiguration {}
}
