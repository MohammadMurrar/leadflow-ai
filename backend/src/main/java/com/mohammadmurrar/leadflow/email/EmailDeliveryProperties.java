package com.mohammadmurrar.leadflow.email;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("leadflow.email-delivery")
public record EmailDeliveryProperties(
        boolean enabled,
        String smtpHost,
        int smtpPort,
        String smtpUsername,
        String smtpPassword,
        boolean smtpAuth,
        boolean smtpStartTls,
        String senderAddress,
        String senderDisplayName,
        String replyTo,
        String publicBaseUrl,
        int batchSize,
        Duration schedulerInterval,
        Duration leaseDuration,
        int maximumDeliveryAttempts,
        Duration initialBackoff,
        Duration maximumBackoff,
        Duration connectionTimeout,
        Duration readTimeout,
        Duration writeTimeout,
        boolean allowHttpLoopback) {

    @ConstructorBinding
    public EmailDeliveryProperties {
    }

    private static final int MAX_BATCH_SIZE = 100;
    private static final int MAX_DELIVERY_ATTEMPTS = 20;
    private static final Duration MIN_SCHEDULER_INTERVAL = Duration.ofSeconds(1);
    private static final Duration MAX_SCHEDULER_INTERVAL = Duration.ofHours(24);
    private static final Duration MAX_LEASE_DURATION = Duration.ofHours(1);
    private static final Duration MAX_INITIAL_BACKOFF = Duration.ofHours(24);
    private static final Duration MAX_BACKOFF = Duration.ofDays(7);
    private static final Duration MAX_SMTP_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration LEASE_SAFETY_MARGIN = Duration.ofSeconds(5);

    public void validate() {
        if (!enabled) return;
        requireRange(batchSize, 1, MAX_BATCH_SIZE, "Email batch size is invalid");
        requireRange(maximumDeliveryAttempts, 1, MAX_DELIVERY_ATTEMPTS,
                "Email maximum delivery attempts is invalid");
        requireRange(smtpPort, 1, 65535, "Email SMTP port is invalid");
        requireRange(schedulerInterval, MIN_SCHEDULER_INTERVAL, MAX_SCHEDULER_INTERVAL,
                "Email scheduler interval is invalid");
        requireRange(leaseDuration, LEASE_SAFETY_MARGIN, MAX_LEASE_DURATION,
                "Email lease duration is invalid");
        requireRange(initialBackoff, Duration.ofSeconds(1), MAX_INITIAL_BACKOFF,
                "Email initial backoff is invalid");
        requireRange(maximumBackoff, Duration.ofSeconds(1), MAX_BACKOFF,
                "Email maximum backoff is invalid");
        requireRange(connectionTimeout, Duration.ofSeconds(1), MAX_SMTP_TIMEOUT,
                "Email connection timeout is invalid");
        requireRange(readTimeout, Duration.ofSeconds(1), MAX_SMTP_TIMEOUT,
                "Email read timeout is invalid");
        requireRange(writeTimeout, Duration.ofSeconds(1), MAX_SMTP_TIMEOUT,
                "Email write timeout is invalid");
        if (initialBackoff.compareTo(maximumBackoff) > 0) invalid("Email backoff configuration is invalid");
        Duration operationWindow;
        try {
            operationWindow = connectionTimeout.plus(readTimeout).plus(writeTimeout)
                    .plus(LEASE_SAFETY_MARGIN);
        } catch (ArithmeticException ex) {
            throw new InvalidEmailDeliveryConfigurationException("Email timeout configuration is invalid");
        }
        if (leaseDuration.compareTo(operationWindow) < 0) invalid("Email lease duration is invalid");
        if (smtpAuth && !smtpStartTls) invalid("Authenticated email delivery requires STARTTLS");
        requireText(smtpHost, "Required email delivery configuration is missing");
        if (smtpAuth) {
            requireText(smtpUsername, "Required email delivery configuration is missing");
            requireText(smtpPassword, "Required email delivery configuration is missing");
        }
        requireEmail(senderAddress, "Required email delivery configuration is invalid");
        requireHeaderText(senderDisplayName, 100, "Required email delivery configuration is invalid");
        if (replyTo != null && !replyTo.isBlank()) requireEmail(replyTo, "Optional email delivery configuration is invalid");
        validatedBaseUri();
    }

    public URI validatedBaseUri() {
        requireText(publicBaseUrl, "Required email delivery configuration is missing");
        try {
            URI uri = URI.create(publicBaseUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            boolean loopback = isLoopback(host);
            boolean permittedScheme = "https".equalsIgnoreCase(scheme)
                    || ("http".equalsIgnoreCase(scheme) && allowHttpLoopback && loopback);
            if (!permittedScheme || host == null || "leadflow.example".equalsIgnoreCase(host)
                    || (!allowHttpLoopback && loopback)
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
                invalid("Public application base URL is invalid");
            }
            return URI.create(scheme.toLowerCase() + "://" + uri.getRawAuthority());
        } catch (IllegalArgumentException ex) {
            throw new InvalidEmailDeliveryConfigurationException("Public application base URL is invalid");
        }
    }

    private static boolean isLoopback(String host) {
        if (host == null) return false;
        String normalized = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1) : host;
        return "localhost".equalsIgnoreCase(normalized) || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized);
    }

    public EmailDeliveryProperties(boolean enabled, String smtpHost, int smtpPort,
            String smtpUsername, String smtpPassword, boolean smtpAuth, boolean smtpStartTls,
            String senderAddress, String senderDisplayName, String replyTo, String publicBaseUrl,
            int batchSize, Duration schedulerInterval, Duration leaseDuration,
            int maximumDeliveryAttempts, Duration initialBackoff, Duration maximumBackoff,
            Duration connectionTimeout, Duration readTimeout, Duration writeTimeout) {
        this(enabled, smtpHost, smtpPort, smtpUsername, smtpPassword, smtpAuth, smtpStartTls,
                senderAddress, senderDisplayName, replyTo, publicBaseUrl, batchSize,
                schedulerInterval, leaseDuration, maximumDeliveryAttempts, initialBackoff,
                maximumBackoff, connectionTimeout, readTimeout, writeTimeout, false);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) invalid(message);
    }
    private static void requireEmail(String value, String message) {
        if (value == null || value.length() > 254 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            invalid(message);
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            if (!address.getAddress().equals(value)) invalid(message);
        } catch (AddressException ex) {
            invalid(message);
        }
    }
    private static void requireHeaderText(String value, int maximumLength, String message) {
        if (value == null || value.isBlank() || value.length() > maximumLength
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) invalid(message);
    }
    private static void requireRange(int value, int minimum, int maximum, String message) {
        if (value < minimum || value > maximum) invalid(message);
    }
    private static void requireRange(Duration value, Duration minimum, Duration maximum, String message) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) invalid(message);
        try {
            if (value.toMillis() > Integer.MAX_VALUE) invalid(message);
        } catch (ArithmeticException ex) {
            throw new InvalidEmailDeliveryConfigurationException(message);
        }
    }
    private static void invalid(String message) { throw new InvalidEmailDeliveryConfigurationException(message); }

    @Override
    public String toString() {
        return "EmailDeliveryProperties[redacted]";
    }

    public static class InvalidEmailDeliveryConfigurationException extends RuntimeException {
        public InvalidEmailDeliveryConfigurationException(String message) { super(message); }
    }
}
