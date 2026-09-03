package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.*;

class PublicFrontendUrlConfigurationTest {

    @Test
    void productionHttpsAndLocalLoopbackGenerateTrustedResetLinks() {
        String token = "A".repeat(43);

        assertThat(renderer("https://app.example.com", false).renderPasswordReset(token).textBody())
                .contains("https://app.example.com/reset-password#token=" + token);
        assertThat(renderer("http://localhost:4173", true).renderPasswordReset(token).textBody())
                .contains("http://localhost:4173/reset-password#token=" + token);
        assertThat(renderer("https://app.example.com/", false).renderPasswordReset(token).textBody())
                .contains("https://app.example.com/reset-password#token=" + token)
                .doesNotContain("app.example.com//reset-password");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void missingProductionConfigurationFailsClosed(String url) {
        assertInvalid(url, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://leadflow.example",
            "http://app.example.com",
            "https://localhost:4173",
            "https://127.0.0.1:4173",
            "not a uri",
            "ftp://app.example.com",
            "https://user@app.example.com",
            "https://app.example.com?source=unsafe",
            "https://app.example.com#fragment",
            "https://app.example.com/reset-password"
    })
    void unsafeProductionConfigurationFailsClosed(String url) {
        assertInvalid(url, false);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:4173",
            "http://127.0.0.1:4173",
            "http://[::1]:4173"
    })
    void localModePermitsOnlyExplicitLoopbackHttp(String url) {
        assertThatCode(() -> properties(url, true).validatedBaseUri()).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://app.example.com",
            "http://user@localhost:4173",
            "http://localhost:4173?unsafe=true",
            "http://localhost:4173#unsafe",
            "http://localhost:4173/reset-password"
    })
    void localModeStillRejectsUntrustedOrMalformedOrigins(String url) {
        assertInvalid(url, true);
    }

    @Test
    void resetTokenContractAllowsOnlyUrlSafeUnpaddedValues() {
        EmailTemplateRenderer renderer = renderer("https://app.example.com", false);

        assertThatThrownBy(() -> renderer.renderPasswordReset("A".repeat(42) + "/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Password reset token is invalid");
        assertThat(renderer.renderPasswordReset("Ab_9-" + "A".repeat(38)).textBody())
                .contains("#token=Ab_9-")
                .doesNotContain("?token=");
    }

    @Test
    void requestMetadataCannotInfluenceTheRendererOrigin() {
        assertThat(Arrays.stream(EmailTemplateRenderer.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .containsExactly(EmailDeliveryProperties.class);
        assertThat(Arrays.stream(EmailTemplateRenderer.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameterTypes())))
                .noneMatch(type -> type.getName().startsWith("jakarta.servlet")
                        || type.getName().startsWith("org.springframework.http"));
    }

    private void assertInvalid(String url, boolean allowLoopback) {
        Throwable thrown = catchThrowable(() -> properties(url, allowLoopback).validatedBaseUri());
        assertThat(thrown)
                .isInstanceOf(EmailDeliveryProperties.InvalidEmailDeliveryConfigurationException.class);
        assertThat(thrown.getMessage())
                .isIn("Required email delivery configuration is missing",
                        "Public application base URL is invalid");
        if (url != null && !url.isEmpty()) {
            assertThat(thrown.getMessage()).doesNotContain(url);
        }
    }

    private EmailTemplateRenderer renderer(String url, boolean allowLoopback) {
        return new EmailTemplateRenderer(properties(url, allowLoopback));
    }

    private EmailDeliveryProperties properties(String url, boolean allowLoopback) {
        EmailDeliveryProperties base = EmailTemplateRendererTest.properties(true, url);
        return new EmailDeliveryProperties(base.enabled(), base.smtpHost(), base.smtpPort(),
                base.smtpUsername(), base.smtpPassword(), base.smtpAuth(), base.smtpStartTls(),
                base.senderAddress(), base.senderDisplayName(), base.replyTo(), url,
                base.batchSize(), base.schedulerInterval(), base.leaseDuration(),
                base.maximumDeliveryAttempts(), base.initialBackoff(), base.maximumBackoff(),
                base.connectionTimeout(), base.readTimeout(), base.writeTimeout(), allowLoopback);
    }
}
