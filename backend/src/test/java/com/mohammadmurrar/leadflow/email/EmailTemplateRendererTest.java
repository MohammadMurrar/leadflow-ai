package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.UUID;
import java.math.BigDecimal;
import com.mohammadmurrar.leadflow.lead.LeadPriority;
import static org.assertj.core.api.Assertions.*;

class EmailTemplateRendererTest {
    @Test
    void passwordResetUsesOnlyAnHttpsFragmentLinkAndApprovedFixedCopy() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(
                properties(true, "https://app.example.invalid"));
        String token = "A".repeat(43);

        RenderedEmail email = renderer.renderPasswordReset(token);

        assertThat(email.subject()).isEqualTo("Reset your LeadFlow password");
        assertThat(email.textBody())
                .contains("expires in 30 minutes", "ignore this message",
                        "https://app.example.invalid/reset-password#token=" + token)
                .doesNotContain("?token=", "/" + token, "@", "requestId", "userId",
                        "nonce", "key version", "hash", "password hash", "lead", "AI");
    }

    @Test
    void rendersOnlyFixedSubjectsAndSafeFixedDashboardPaths() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(properties(true, "https://app.example.invalid"));

        assertThat(renderer.render(EmailTemplateType.NEW_INQUIRY))
                .isEqualTo(new RenderedEmail("New inquiry received",
                        "A new inquiry is ready for review." + System.lineSeparator()
                                + System.lineSeparator()
                                + "Open LeadFlow: https://app.example.invalid/leads"));
        assertThat(renderer.render(EmailTemplateType.QUALIFICATION_COMPLETED).subject())
                .isEqualTo("Lead qualification completed");
        assertThat(renderer.render(EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION).subject())
                .isEqualTo("Lead qualification needs attention");
        RenderedEmail password = renderer.render(EmailTemplateType.PASSWORD_CHANGED);
        assertThat(password.subject()).isEqualTo("Your LeadFlow password was changed");
        assertThat(password.textBody()).contains("https://app.example.invalid")
                .doesNotContain("leadId", "token", "email", "phone", "summary", "reply");
    }

    @Test
    void eventTemplatesContainOnlyApprovedPrivacyMinimizedLeadFields() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(
                properties(true, "https://app.example.invalid"));
        String forbidden = "forbidden-email@example.invalid forbidden-phone forbidden-message "
                + "forbidden-budget forbidden-date forbidden-ai forbidden-id forbidden-source forbidden-token";

        RenderedEmail newLead = renderer.render(claim(EmailTemplateType.NEW_INQUIRY,
                "Distinctive Lead", "Distinctive Company", "Distinctive Service", null,
                LeadPriority.UNASSESSED));
        assertThat(newLead.subject()).isEqualTo("New inquiry received");
        assertThat(newLead.textBody()).contains("Distinctive Lead", "Distinctive Company",
                "Distinctive Service", "https://app.example.invalid/leads")
                .doesNotContain(forbidden.split(" "));

        RenderedEmail success = renderer.render(claim(EmailTemplateType.QUALIFICATION_COMPLETED,
                "Distinctive Lead", "Distinctive Company", "Distinctive Service", 91,
                LeadPriority.HIGH));
        assertThat(success.textBody()).contains("Distinctive Lead", "Distinctive Service", "91", "HIGH",
                "https://app.example.invalid/leads")
                .doesNotContain("Distinctive Company")
                .doesNotContain(forbidden.split(" "));

        RenderedEmail failure = renderer.render(claim(EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION,
                "Distinctive Lead", "Distinctive Company", "Distinctive Service", null,
                LeadPriority.UNASSESSED));
        assertThat(failure.textBody()).contains("Distinctive Lead", "Distinctive Service",
                "Automated qualification did not complete.", "https://app.example.invalid/leads")
                .doesNotContain("Distinctive Company", "UNASSESSED")
                .doesNotContain(forbidden.split(" "));
    }

    @Test
    void inquiryBudgetUsesTheClaimedWorkspaceCurrencyWithoutConversion() {
        EmailTemplateRenderer renderer = new EmailTemplateRenderer(
                properties(true, "https://app.example.invalid"));
        ClaimedEmail ils = new ClaimedEmail(UUID.randomUUID(), UUID.randomUUID(), "lease",
                EmailTemplateType.NEW_INQUIRY, "recipient@example.invalid", 1,
                "Lead", null, "Service", null, LeadPriority.UNASSESSED,
                new BigDecimal("4000.00"), "ILS", null);
        ClaimedEmail eur = new ClaimedEmail(UUID.randomUUID(), ils.workspaceId(), "lease",
                EmailTemplateType.NEW_INQUIRY, "recipient@example.invalid", 1,
                "Lead", null, "Service", null, LeadPriority.UNASSESSED,
                new BigDecimal("4000.00"), "EUR", null);

        assertThat(renderer.render(ils).textBody()).contains("Estimated budget: ₪4,000.00");
        assertThat(renderer.render(eur).textBody()).contains("Estimated budget: €4,000.00");
        assertThat(ils.estimatedBudget()).isEqualByComparingTo(eur.estimatedBudget());
    }

    private ClaimedEmail claim(EmailTemplateType type, String name, String company,
            String service, Integer score, LeadPriority priority) {
        return new ClaimedEmail(UUID.randomUUID(), "forbidden-token", type,
                "forbidden-recipient@example.invalid", 1, name, company, service, score, priority);
    }

    @Test
    void validatesConfigurationWithoutIncludingSecretValues() {
        assertThatCode(() -> properties(false, "").validate()).doesNotThrowAnyException();
        EmailDeliveryProperties configured = properties(true, "https://app.example.invalid");
        EmailDeliveryProperties missingSecret = new EmailDeliveryProperties(configured.enabled(),
                configured.smtpHost(), configured.smtpPort(), configured.smtpUsername(), null,
                configured.smtpAuth(), configured.smtpStartTls(), configured.senderAddress(),
                configured.senderDisplayName(), configured.replyTo(), configured.publicBaseUrl(),
                configured.batchSize(), configured.schedulerInterval(), configured.leaseDuration(),
                configured.maximumDeliveryAttempts(), configured.initialBackoff(),
                configured.maximumBackoff(), configured.connectionTimeout(), configured.readTimeout(),
                configured.writeTimeout());
        assertThatThrownBy(missingSecret::validate)
                .hasMessage("Required email delivery configuration is missing")
                .hasMessageNotContaining("test-password");
        assertThatThrownBy(() -> properties(true, "http://app.example.invalid").validate())
                .isInstanceOf(EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class)
                .hasMessage("Public application base URL is invalid")
                .hasMessageNotContaining("test-password");
        EmailDeliveryProperties sentinels = new EmailDeliveryProperties(true,
                "distinctive-host.invalid", 587, "distinctive-username", "distinctive-password",
                true, true, "distinctive-sender@example.invalid", "Distinctive Name",
                "distinctive-reply@example.invalid", "https://distinctive-app.invalid", 10,
                Duration.ofSeconds(1), Duration.ofSeconds(26), 3, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(7), Duration.ofSeconds(7), Duration.ofSeconds(7));
        assertThat(sentinels.toString()).isEqualTo("EmailDeliveryProperties[redacted]")
                .doesNotContain("distinctive", "username", "password", "example.invalid");
    }

    @Test
    void validatesTransportSecurityAndOperationalBounds() {
        EmailDeliveryProperties valid = properties(true, "https://app.example.invalid");
        assertThatThrownBy(() -> copy(valid, true, false, 587, 10, 3,
                Duration.ofHours(1), Duration.ofMinutes(2), Duration.ofSeconds(10),
                Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofSeconds(6),
                Duration.ofSeconds(7)).validate())
                .hasMessage("Authenticated email delivery requires STARTTLS");

        EmailDeliveryProperties local = copy(valid, false, false, 2525, 10, 3,
                Duration.ofHours(1), Duration.ofMinutes(2), Duration.ofSeconds(10),
                Duration.ofMinutes(1), Duration.ofSeconds(5), Duration.ofSeconds(6),
                Duration.ofSeconds(7));
        assertThatCode(local::validate).doesNotThrowAnyException();

        assertInvalid(copy(valid, true, true, 65536, 10, 3, Duration.ofHours(1),
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 101, 3, Duration.ofHours(1),
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 10, 21, Duration.ofHours(1),
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 10, 3, Duration.ofMillis(999),
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 10, 3, Duration.ofHours(1),
                Duration.ofSeconds(20), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 10, 3, Duration.ofHours(1),
                Duration.ofMinutes(2), Duration.ofMinutes(2), Duration.ofMinutes(1),
                Duration.ofSeconds(5), Duration.ofSeconds(6), Duration.ofSeconds(7)));
        assertInvalid(copy(valid, true, true, 587, 10, 3, Duration.ofHours(1),
                Duration.ofMinutes(2), Duration.ofSeconds(10), Duration.ofMinutes(1),
                Duration.ofMinutes(3), Duration.ofSeconds(6), Duration.ofSeconds(7)));

        EmailDeliveryProperties injectedHeader = new EmailDeliveryProperties(true,
                valid.smtpHost(), valid.smtpPort(), valid.smtpUsername(), valid.smtpPassword(),
                true, true, valid.senderAddress(), "LeadFlow\r\nBcc: hidden@example.invalid",
                null, valid.publicBaseUrl(), valid.batchSize(), valid.schedulerInterval(),
                valid.leaseDuration(), valid.maximumDeliveryAttempts(), valid.initialBackoff(),
                valid.maximumBackoff(), valid.connectionTimeout(), valid.readTimeout(),
                valid.writeTimeout());
        assertInvalid(injectedHeader);
    }

    private void assertInvalid(EmailDeliveryProperties properties) {
        assertThatThrownBy(properties::validate)
                .isInstanceOf(EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class)
                .hasMessageNotContaining("test-password");
    }

    private EmailDeliveryProperties copy(EmailDeliveryProperties base, boolean auth, boolean tls,
            int port, int batch, int attempts, Duration interval, Duration lease,
            Duration initialBackoff, Duration maximumBackoff, Duration connect,
            Duration read, Duration write) {
        return new EmailDeliveryProperties(true, base.smtpHost(), port,
                auth ? base.smtpUsername() : null, auth ? base.smtpPassword() : null, auth, tls,
                base.senderAddress(), base.senderDisplayName(), base.replyTo(), base.publicBaseUrl(),
                batch, interval, lease, attempts, initialBackoff, maximumBackoff,
                connect, read, write);
    }

    static EmailDeliveryProperties properties(boolean enabled, String baseUrl) {
        return new EmailDeliveryProperties(enabled, "mail.example.invalid", 587, "test-user",
                "test-password", true, true, "sender@example.invalid", "LeadFlow", null,
                baseUrl, 10, Duration.ofHours(1), Duration.ofMinutes(2), 3,
                Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofSeconds(5),
                Duration.ofSeconds(6), Duration.ofSeconds(7));
    }
}
