package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class EmailDeliveryPropertiesBoundaryTest {
    @Test
    void acceptsEveryExactNumericAndDurationBoundary() {
        assertValid(properties(1, 1, 1, Duration.ofSeconds(1), Duration.ofSeconds(8),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
        assertValid(properties(65535, 100, 20, Duration.ofHours(24), Duration.ofHours(1),
                Duration.ofHours(24), Duration.ofDays(7), Duration.ofMinutes(2),
                Duration.ofMinutes(2), Duration.ofMinutes(2)));
        assertValid(properties(587, 10, 3, Duration.ofSeconds(1), Duration.ofSeconds(8),
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsEveryValueImmediatelyOutsideItsBoundary() {
        EmailDeliveryProperties valid = properties(587, 10, 3, Duration.ofMinutes(1),
                Duration.ofMinutes(7), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(5));
        List<EmailDeliveryProperties> invalid = List.of(
                copy(valid, 0, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 65536, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 0, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 101, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 0, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 21, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, Duration.ofMillis(999), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, Duration.ofHours(24).plusMillis(1), valid.leaseDuration(), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), Duration.ofMillis(4999), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), Duration.ofHours(1).plusMillis(1), valid.initialBackoff(), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), Duration.ofMillis(999), valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), Duration.ofHours(24).plusMillis(1), Duration.ofDays(2), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), Duration.ofMillis(999), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                copy(valid, 587, 10, 3, valid.schedulerInterval(), valid.leaseDuration(), valid.initialBackoff(), Duration.ofDays(7).plusMillis(1), valid.connectionTimeout(), valid.readTimeout(), valid.writeTimeout()),
                withTimeout(valid, Duration.ofMillis(999), valid.readTimeout(), valid.writeTimeout()),
                withTimeout(valid, Duration.ofMinutes(2).plusMillis(1), valid.readTimeout(), valid.writeTimeout()),
                withTimeout(valid, valid.connectionTimeout(), Duration.ofMillis(999), valid.writeTimeout()),
                withTimeout(valid, valid.connectionTimeout(), Duration.ofMinutes(2).plusMillis(1), valid.writeTimeout()),
                withTimeout(valid, valid.connectionTimeout(), valid.readTimeout(), Duration.ofMillis(999)),
                withTimeout(valid, valid.connectionTimeout(), valid.readTimeout(), Duration.ofMinutes(2).plusMillis(1)),
                withTimeout(valid, Duration.ofMillis(Integer.MAX_VALUE + 1L), valid.readTimeout(), valid.writeTimeout()));
        invalid.forEach(value -> assertThatThrownBy(value::validate)
                .isInstanceOf(EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class)
                .hasMessageNotContaining("test-user")
                .hasMessageNotContaining("test-password")
                .hasMessageNotContaining("mail.example.invalid"));
    }

    @Test
    void validatesCrossFieldLeaseAndBackoffBoundaries() {
        EmailDeliveryProperties base = properties(587, 10, 3, Duration.ofMinutes(1),
                Duration.ofSeconds(35), Duration.ofSeconds(10), Duration.ofSeconds(10),
                Duration.ofSeconds(10), Duration.ofSeconds(10), Duration.ofSeconds(10));
        assertValid(base); // 30 seconds of timeouts plus the 5-second safety margin is valid.
        assertThatThrownBy(() -> copy(base, 587, 10, 3, base.schedulerInterval(),
                Duration.ofMillis(34_999), base.initialBackoff(), base.maximumBackoff(),
                base.connectionTimeout(), base.readTimeout(), base.writeTimeout()).validate())
                .hasMessage("Email lease duration is invalid");
        assertValid(copy(base, 587, 10, 3, base.schedulerInterval(), base.leaseDuration(),
                Duration.ofSeconds(10), Duration.ofSeconds(10), base.connectionTimeout(),
                base.readTimeout(), base.writeTimeout()));
        assertThatThrownBy(() -> copy(base, 587, 10, 3, base.schedulerInterval(), base.leaseDuration(),
                Duration.ofSeconds(11), Duration.ofSeconds(10), base.connectionTimeout(),
                base.readTimeout(), base.writeTimeout()).validate())
                .hasMessage("Email backoff configuration is invalid");
    }

    private void assertValid(EmailDeliveryProperties value) { assertThatCode(value::validate).doesNotThrowAnyException(); }

    private EmailDeliveryProperties properties(int port, int batch, int attempts, Duration interval,
            Duration lease, Duration initial, Duration maximum, Duration connect, Duration read, Duration write) {
        return new EmailDeliveryProperties(true, "mail.example.invalid", port, "test-user", "test-password",
                true, true, "sender@example.invalid", "LeadFlow", null, "https://app.example.invalid",
                batch, interval, lease, attempts, initial, maximum, connect, read, write);
    }

    private EmailDeliveryProperties copy(EmailDeliveryProperties base, int port, int batch, int attempts,
            Duration interval, Duration lease, Duration initial, Duration maximum,
            Duration connect, Duration read, Duration write) {
        return new EmailDeliveryProperties(true, base.smtpHost(), port, base.smtpUsername(), base.smtpPassword(),
                true, true, base.senderAddress(), base.senderDisplayName(), base.replyTo(), base.publicBaseUrl(),
                batch, interval, lease, attempts, initial, maximum, connect, read, write);
    }

    private EmailDeliveryProperties withTimeout(EmailDeliveryProperties base,
            Duration connect, Duration read, Duration write) {
        return copy(base, base.smtpPort(), base.batchSize(), base.maximumDeliveryAttempts(),
                base.schedulerInterval(), base.leaseDuration(), base.initialBackoff(),
                base.maximumBackoff(), connect, read, write);
    }
}
