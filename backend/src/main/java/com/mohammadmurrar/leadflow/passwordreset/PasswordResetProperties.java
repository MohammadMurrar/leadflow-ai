package com.mohammadmurrar.leadflow.passwordreset;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@ConfigurationProperties("leadflow.password-reset")
public record PasswordResetProperties(
        boolean enabled,
        String activeKeyVersion,
        String hmacKeys,
        @DefaultValue("PT30M") Duration tokenLifetime,
        @DefaultValue("PT5M") Duration requestCooldown,
        @DefaultValue("PT720H") Duration retention) {
    private static final Pattern VERSION = Pattern.compile("^[a-z][a-z0-9_-]{0,15}$");
    private static final Pattern BASE64_URL = Pattern.compile("^[A-Za-z0-9_-]+$");
    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final Duration MINIMUM_COOLDOWN = Duration.ofSeconds(1);
    private static final Duration MINIMUM_RETENTION = Duration.ofDays(1);
    private static final Duration MAXIMUM_RETENTION = Duration.ofDays(365);
    private static final int MAXIMUM_KEY_COUNT = 4;
    private static final int MAXIMUM_KEY_LENGTH = 64;
    private static final int MAXIMUM_ENCODED_KEY_LENGTH = 86;
    private static final int MAXIMUM_ENTRY_LENGTH = 103;
    private static final int MAXIMUM_KEY_RING_LENGTH = 2048;

    public PasswordResetKeyRing validatedKeyRing() {
        validateDurations();
        if (!enabled) return PasswordResetKeyRing.disabled();
        if (activeKeyVersion == null || !VERSION.matcher(activeKeyVersion).matches()
                || hmacKeys == null || hmacKeys.isBlank()
                || hmacKeys.length() > MAXIMUM_KEY_RING_LENGTH) {
            throw invalid();
        }
        Map<String, byte[]> keys = new LinkedHashMap<>();
        try {
            String[] entries = hmacKeys.split(",", -1);
            if (entries.length == 0 || entries.length > MAXIMUM_KEY_COUNT) throw invalid();
            for (String entry : entries) {
                if (entry.length() > MAXIMUM_ENTRY_LENGTH) throw invalid();
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator != entry.lastIndexOf('=')) throw invalid();
                String version = entry.substring(0, separator);
                String encoded = entry.substring(separator + 1);
                if (!VERSION.matcher(version).matches() || encoded.length() > MAXIMUM_ENCODED_KEY_LENGTH
                        || !BASE64_URL.matcher(encoded).matches() || keys.containsKey(version)) {
                    throw invalid();
                }
                byte[] key;
                try {
                    key = Base64.getUrlDecoder().decode(encoded);
                } catch (IllegalArgumentException exception) {
                    throw invalid();
                }
                try {
                    if (key.length < 32 || key.length > MAXIMUM_KEY_LENGTH
                            || !Base64.getUrlEncoder().withoutPadding()
                            .encodeToString(key).equals(encoded)) throw invalid();
                    keys.put(version, key);
                    key = null;
                } finally {
                    if (key != null) Arrays.fill(key, (byte) 0);
                }
            }
            if (!keys.containsKey(activeKeyVersion)) throw invalid();
            return PasswordResetKeyRing.enabled(activeKeyVersion, keys);
        } catch (InvalidPasswordResetConfigurationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid();
        } finally {
            keys.values().forEach(key -> Arrays.fill(key, (byte) 0));
        }
    }

    private void validateDurations() {
        if (!TOKEN_LIFETIME.equals(tokenLifetime) || requestCooldown == null
                || requestCooldown.compareTo(MINIMUM_COOLDOWN) < 0
                || requestCooldown.compareTo(TOKEN_LIFETIME) > 0
                || retention == null || retention.compareTo(MINIMUM_RETENTION) < 0
                || retention.compareTo(MAXIMUM_RETENTION) > 0
                || retention.compareTo(TOKEN_LIFETIME) <= 0) throw invalid();
    }

    public Instant tokenExpiresAt(Instant createdAt) {
        validateDurations();
        return checkedAdd(createdAt, tokenLifetime);
    }

    public Instant cooldownEndsAt(Instant createdAt) {
        validateDurations();
        return checkedAdd(createdAt, requestCooldown);
    }

    public Instant retentionEndsAt(Instant terminalAt) {
        validateDurations();
        return checkedAdd(terminalAt, retention);
    }

    private static Instant checkedAdd(Instant value, Duration duration) {
        try {
            return Objects.requireNonNull(value).plus(duration);
        } catch (DateTimeException | ArithmeticException | NullPointerException exception) {
            throw invalid();
        }
    }

    private static InvalidPasswordResetConfigurationException invalid() {
        return new InvalidPasswordResetConfigurationException();
    }

    @Override
    public String toString() { return "PasswordResetProperties[redacted]"; }

    public static final class InvalidPasswordResetConfigurationException extends RuntimeException {
        public InvalidPasswordResetConfigurationException() {
            super("Password reset configuration is invalid");
        }
    }
}
