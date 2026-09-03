package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.*;

import static org.assertj.core.api.Assertions.*;

class EmailEnabledContextTest {
    @Test
    void enabledContextRejectsMissingConfigurationWithoutSensitiveDiagnostics() {
        assertStartupFailure(missingRequiredProperties(), "Required email delivery configuration is missing");
    }

    @Test
    void authenticatedContextRejectsDisabledStartTlsWithoutSensitiveDiagnostics() {
        assertStartupFailure(validProperties(false), "Authenticated email delivery requires STARTTLS");
    }

    @Test
    void disabledContextStartsWithoutSmtpConfiguration() {
        try (AnnotationConfigApplicationContext context = context(
                "leadflow.email-delivery.enabled=false")) {
            assertThat(context.getBean(EmailDeliveryProperties.class).enabled()).isFalse();
            assertThat(context.getBean(SmtpEmailSender.class)).isNotNull();
        }
    }

    private void assertStartupFailure(String[] values, String safeMessage) {
        Throwable thrown = catchThrowable(() -> {
            try (AnnotationConfigApplicationContext ignored = context(values)) {
                // Context creation is the operation under test.
            }
        });
        assertThat(thrown).hasRootCauseInstanceOf(
                EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class);
        String diagnostics = stackTrace(thrown);
        assertThat(diagnostics).contains(safeMessage)
                .doesNotContain("distinctive-user", "distinctive-password");
    }

    private AnnotationConfigApplicationContext context(String... values) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        TestPropertyValues.of(values).applyTo(context);
        context.register(Configuration.class);
        context.refresh();
        return context;
    }

    private String[] validProperties(boolean tls) {
        return new String[] {
                "leadflow.email-delivery.enabled=true",
                "leadflow.email-delivery.smtp-host=mail.example.invalid",
                "leadflow.email-delivery.smtp-port=587",
                "leadflow.email-delivery.smtp-username=distinctive-user",
                "leadflow.email-delivery.smtp-password=distinctive-password",
                "leadflow.email-delivery.smtp-auth=true",
                "leadflow.email-delivery.smtp-start-tls=" + tls,
                "leadflow.email-delivery.sender-address=sender@example.invalid",
                "leadflow.email-delivery.sender-display-name=LeadFlow",
                "leadflow.email-delivery.public-base-url=https://app.example.invalid",
                "leadflow.email-delivery.batch-size=10",
                "leadflow.email-delivery.scheduler-interval=PT30S",
                "leadflow.email-delivery.lease-duration=PT2M",
                "leadflow.email-delivery.maximum-delivery-attempts=5",
                "leadflow.email-delivery.initial-backoff=PT30S",
                "leadflow.email-delivery.maximum-backoff=PT30M",
                "leadflow.email-delivery.connection-timeout=PT10S",
                "leadflow.email-delivery.read-timeout=PT10S",
                "leadflow.email-delivery.write-timeout=PT10S"
        };
    }

    private String[] missingRequiredProperties() {
        return new String[] {
                "leadflow.email-delivery.enabled=true",
                "leadflow.email-delivery.smtp-port=587",
                "leadflow.email-delivery.smtp-auth=false",
                "leadflow.email-delivery.smtp-start-tls=false",
                "leadflow.email-delivery.sender-address=sender@example.invalid",
                "leadflow.email-delivery.sender-display-name=LeadFlow",
                "leadflow.email-delivery.public-base-url=https://app.example.invalid",
                "leadflow.email-delivery.batch-size=10",
                "leadflow.email-delivery.scheduler-interval=PT30S",
                "leadflow.email-delivery.lease-duration=PT2M",
                "leadflow.email-delivery.maximum-delivery-attempts=5",
                "leadflow.email-delivery.initial-backoff=PT30S",
                "leadflow.email-delivery.maximum-backoff=PT30M",
                "leadflow.email-delivery.connection-timeout=PT10S",
                "leadflow.email-delivery.read-timeout=PT10S",
                "leadflow.email-delivery.write-timeout=PT10S"
        };
    }

    private String stackTrace(Throwable failure) {
        java.io.StringWriter output = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(output));
        return output.toString();
    }

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailDeliveryProperties.class)
    static class Configuration {
        @Bean
        SmtpEmailSender smtpEmailSender(EmailDeliveryProperties properties) {
            return new SmtpEmailSender(properties);
        }
    }
}
