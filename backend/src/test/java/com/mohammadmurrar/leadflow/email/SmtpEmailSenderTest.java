package com.mohammadmurrar.leadflow.email;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.*;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.MailParseException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SmtpEmailSenderTest {
    @Test
    void mapsFromReplyToRecipientAndFixedContentWithoutSendingDuringSetup() throws Exception {
        var base = EmailTemplateRendererTest.properties(true, "https://app.example.invalid");
        var properties = new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                base.senderAddress(), base.senderDisplayName(), "reply@example.invalid",
                base.publicBaseUrl(), base.batchSize(), base.schedulerInterval(), base.leaseDuration(),
                base.maximumDeliveryAttempts(), base.initialBackoff(), base.maximumBackoff(),
                base.connectionTimeout(), base.readTimeout(), base.writeTimeout());
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new JavaMailSenderImpl().createMimeMessage();
        when(sender.createMimeMessage()).thenReturn(message);
        SmtpEmailSender adapter = new SmtpEmailSender(properties, sender);

        adapter.send("recipient@example.invalid", new RenderedEmail("Fixed subject", "Fixed body"));

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(sent.capture());
        assertThat(sent.getValue().getAllRecipients()[0].toString()).isEqualTo("recipient@example.invalid");
        assertThat(sent.getValue().getFrom()[0].toString()).contains("LeadFlow", "sender@example.invalid");
        assertThat(sent.getValue().getReplyTo()[0].toString()).isEqualTo("reply@example.invalid");
        assertThat(sent.getValue().getSubject()).isEqualTo("Fixed subject");
        assertThat(sent.getValue().getContent().toString()).contains("Fixed body");
    }

    @Test
    void configuresTimeoutsAndClassifiesWithoutProviderText() {
        var properties = EmailTemplateRendererTest.properties(true, "https://app.example.invalid");
        JavaMailSenderImpl sender = (JavaMailSenderImpl) SmtpEmailSender.configuredSender(properties);
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.connectiontimeout", "5000")
                .containsEntry("mail.smtp.timeout", "6000")
                .containsEntry("mail.smtp.writetimeout", "7000")
                .containsEntry("mail.smtp.auth", "true")
                .containsEntry("mail.smtp.starttls.enable", "true");
        assertThat(sender.getJavaMailProperties())
                .containsEntry("mail.smtp.starttls.required", "true")
                .doesNotContainKey("mail.debug");
        assertThat(SmtpEmailSender.classify(new MailAuthenticationException("provider secret")))
                .isEqualTo(EmailFailureCode.AUTHENTICATION);
        assertThat(SmtpEmailSender.classify(new RuntimeException(new SocketTimeoutException("host"))))
                .isEqualTo(EmailFailureCode.TIMEOUT);
        assertThat(SmtpEmailSender.classify(new RuntimeException(new ConnectException("host"))))
                .isEqualTo(EmailFailureCode.CONNECTION);
        assertThat(SmtpEmailSender.classify(new MailSendException("provider detail")))
                .isEqualTo(EmailFailureCode.REJECTED);
        assertThat(SmtpEmailSender.classify(new MailPreparationException("configuration detail")))
                .isEqualTo(EmailFailureCode.INVALID_CONFIGURATION);
        assertThat(SmtpEmailSender.classify(new IllegalStateException("unexpected detail")))
                .isEqualTo(EmailFailureCode.UNEXPECTED);
        assertThat(new EmailDeliveryException(EmailFailureCode.REJECTED).getMessage())
                .isEqualTo("Email provider did not accept the message")
                .doesNotContain("provider secret", "recipient@example.invalid");
    }

    @Test
    void rejectsInvalidAndInjectedAddressesWithFixedSafeClassification() {
        for (AddressCase address : java.util.List.of(
                new AddressCase("invalid-recipient", "sender@example.invalid", null),
                new AddressCase("recipient@example.invalid", "invalid-sender", null),
                new AddressCase("recipient@example.invalid", "sender@example.invalid", "invalid-reply"),
                new AddressCase("recipient\r@example.invalid", "sender@example.invalid", null),
                new AddressCase("recipient\n@example.invalid", "sender@example.invalid", null),
                new AddressCase("recipient@example.invalid", "sender\r@example.invalid", null),
                new AddressCase("recipient@example.invalid", "sender\n@example.invalid", null),
                new AddressCase("recipient@example.invalid", "sender@example.invalid", "reply\r@example.invalid"),
                new AddressCase("recipient@example.invalid", "sender@example.invalid", "reply\n@example.invalid"))) {
            JavaMailSender sender = mock(JavaMailSender.class);
            SmtpEmailSender adapter = new SmtpEmailSender(withAddresses(address.sender(), address.replyTo()), sender);
            assertThatThrownBy(() -> adapter.send(address.recipient(),
                    new RenderedEmail("Fixed subject", "Fixed body")))
                    .isInstanceOf(EmailDeliveryException.class)
                    .extracting(ex -> ((EmailDeliveryException) ex).failureCode())
                    .isEqualTo(EmailFailureCode.INVALID_CONFIGURATION);
            verify(sender, never()).send(any(MimeMessage.class));
        }
    }

    @Test
    void messageConstructionFailureIsSafeAndDoesNotReachTransport() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenThrow(new MailParseException("sensitive-address-value"));
        SmtpEmailSender adapter = new SmtpEmailSender(
                EmailTemplateRendererTest.properties(true, "https://app.example.invalid"), sender);

        assertThatThrownBy(() -> adapter.send("recipient@example.invalid",
                new RenderedEmail("Fixed subject", "Fixed body")))
                .isInstanceOfSatisfying(EmailDeliveryException.class, ex -> {
                    assertThat(ex.failureCode()).isEqualTo(EmailFailureCode.INVALID_CONFIGURATION);
                    assertThat(ex.toString()).doesNotContain("sensitive-address-value", "recipient@example.invalid");
                });
        verify(sender, never()).send(any(MimeMessage.class));
    }

    private EmailDeliveryProperties withAddresses(String sender, String replyTo) {
        var base = EmailTemplateRendererTest.properties(true, "https://app.example.invalid");
        return new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                sender, base.senderDisplayName(), replyTo, base.publicBaseUrl(), base.batchSize(),
                base.schedulerInterval(), base.leaseDuration(), base.maximumDeliveryAttempts(),
                base.initialBackoff(), base.maximumBackoff(), base.connectionTimeout(),
                base.readTimeout(), base.writeTimeout());
    }

    private record AddressCase(String recipient, String sender, String replyTo) {}
}
