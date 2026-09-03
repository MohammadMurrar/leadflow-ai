package com.mohammadmurrar.leadflow.email;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.*;
import org.springframework.mail.javamail.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Component
public class SmtpEmailSender implements EmailSender {
    private final EmailDeliveryProperties properties;
    private final JavaMailSender sender;

    @Autowired
    public SmtpEmailSender(EmailDeliveryProperties properties) {
        this(properties, configuredSender(properties));
    }

    SmtpEmailSender(EmailDeliveryProperties properties, JavaMailSender sender) {
        this.properties = properties;
        this.sender = sender;
    }

    @Override
    public void send(String recipient, RenderedEmail email) {
        if (!properties.enabled()) throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
        validateAddress(recipient);
        validateAddress(properties.senderAddress());
        if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
            validateAddress(properties.replyTo());
        }
        final MimeMessage message;
        try {
            message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setTo(recipient);
            helper.setFrom(properties.senderAddress(), properties.senderDisplayName());
            if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
                helper.setReplyTo(properties.replyTo().trim());
            }
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), false);
        } catch (RuntimeException | MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
        }
        try {
            sender.send(message);
        } catch (RuntimeException ex) {
            throw new EmailDeliveryException(classify(ex));
        }
    }

    private static void validateAddress(String value) {
        if (value == null || value.length() > 254 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            if (!address.getAddress().equals(value)) {
                throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
            }
        } catch (AddressException ex) {
            throw new EmailDeliveryException(EmailFailureCode.INVALID_CONFIGURATION);
        }
    }

    static JavaMailSender configuredSender(EmailDeliveryProperties properties) {
        properties.validate();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        if (!properties.enabled()) return sender;
        sender.setHost(properties.smtpHost().trim());
        sender.setPort(properties.smtpPort());
        sender.setUsername(blankToNull(properties.smtpUsername()));
        sender.setPassword(blankToNull(properties.smtpPassword()));
        Properties mail = sender.getJavaMailProperties();
        mail.setProperty("mail.smtp.auth", Boolean.toString(properties.smtpAuth()));
        mail.setProperty("mail.smtp.starttls.enable", Boolean.toString(properties.smtpStartTls()));
        mail.setProperty("mail.smtp.starttls.required", Boolean.toString(properties.smtpStartTls()));
        mail.setProperty("mail.smtp.connectiontimeout", Long.toString(properties.connectionTimeout().toMillis()));
        mail.setProperty("mail.smtp.timeout", Long.toString(properties.readTimeout().toMillis()));
        mail.setProperty("mail.smtp.writetimeout", Long.toString(properties.writeTimeout().toMillis()));
        return sender;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static EmailFailureCode classify(Throwable failure) {
        boolean rejected = false;
        boolean invalidConfiguration = false;
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof AuthenticationFailedException || current instanceof MailAuthenticationException) {
                return EmailFailureCode.AUTHENTICATION;
            }
            if (current instanceof SocketTimeoutException) return EmailFailureCode.TIMEOUT;
            if (current instanceof ConnectException || current instanceof UnknownHostException) {
                return EmailFailureCode.CONNECTION;
            }
            if (current instanceof MailPreparationException) invalidConfiguration = true;
            if (current instanceof MailSendException) rejected = true;
        }
        if (invalidConfiguration) return EmailFailureCode.INVALID_CONFIGURATION;
        if (rejected) return EmailFailureCode.REJECTED;
        return EmailFailureCode.UNEXPECTED;
    }
}
