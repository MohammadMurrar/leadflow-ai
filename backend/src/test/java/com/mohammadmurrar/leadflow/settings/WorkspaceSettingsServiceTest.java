package com.mohammadmurrar.leadflow.settings;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.qualification.QualificationReliabilityProperties;
import com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.Optional;
import java.util.List;
import com.mohammadmurrar.leadflow.workspace.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceSettingsServiceTest {
    @Mock WorkspaceSettingsRepository repository;
    @Mock CurrentWorkspace currentWorkspace;

    @Test
    void initializesMissingWorkspaceWithoutCrossingWorkspaceBoundary() {
        WorkspaceSettings existing = settings();
        Workspace workspace = existing.getWorkspace();
        when(currentWorkspace.requireActive()).thenReturn(workspace);
        when(repository.findByWorkspaceId(workspace.getId()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        var response = service().findWorkspace();
        verify(repository).initializeIfMissing(any(), eq(workspace.getId()));
        verify(currentWorkspace).requireActive();
        assertThat(response.workspaceName()).isEqualTo("My Workspace");
    }

    @Test
    void trimsUpdatesAndLeavesNoOpVersionAndTimestampUntouched() {
        WorkspaceSettings existing = settings();
        stubExisting(existing);
        var updated = service().update(request(0,
                "  Revenue   Operations  ", "  ops@example.com  ", "  Qualified leads  "));
        assertThat(updated.workspaceName()).isEqualTo("Revenue Operations");
        assertThat(updated.contactEmail()).isEqualTo("ops@example.com");
        assertThat(updated.description()).isEqualTo("Qualified leads");
        verify(repository).flush();

        reset(repository);
        stubExisting(existing);
        service().update(request(0,
                "Revenue Operations", "ops@example.com", "Qualified leads"));
        verify(repository, never()).flush();
    }

    @Test
    void rejectsInvalidEmailDescriptionAndStaleVersion() {
        WorkspaceSettings existing = settings();
        stubExisting(existing);
        assertThatThrownBy(() -> service().update(request(0,
                "Workspace", "not-email", null)))
                .isInstanceOf(WorkspaceSettingsService.InvalidWorkspaceSettingsException.class);
        assertThatThrownBy(() -> service().update(request(0,
                "Workspace", null, "x".repeat(501))))
                .isInstanceOf(WorkspaceSettingsService.InvalidWorkspaceSettingsException.class);
        assertThatThrownBy(() -> service().update(request(4,
                "Workspace", null, null))).isInstanceOf(ConflictException.class);
    }

    @Test
    void normalizesAllBusinessFieldsAndDeterministicallyOrdersRecipients() {
        WorkspaceSettings existing = settings();
        stubExisting(existing);

        var updated = service().update(new UpdateWorkspaceSettingsRequest(0, " Workspace ", null, null,
                " Public Brand ", " Trusted automation ", " /assets/client-logo.svg ",
                " Asia/Jerusalem ", " ils ", " We respond within two hours. ",
                " https://example.com/privacy ", " Privacy notice ", " 2026-08 ",
                List.of(" OWNER@EXAMPLE.COM ", "alerts@example.com")));

        assertThat(updated.publicBrandName()).isEqualTo("Public Brand");
        assertThat(updated.publicTagline()).isEqualTo("Trusted automation");
        assertThat(updated.publicLogoPath()).isEqualTo("/assets/client-logo.svg");
        assertThat(updated.timeZone()).isEqualTo("Asia/Jerusalem");
        assertThat(updated.currency()).isEqualTo("ILS");
        assertThat(updated.responseTimeText()).isEqualTo("We respond within two hours.");
        assertThat(updated.privacyPolicyUrl()).isEqualTo("https://example.com/privacy");
        assertThat(updated.privacyNoticeText()).isEqualTo("Privacy notice");
        assertThat(updated.privacyNoticeVersion()).isEqualTo("2026-08");
        assertThat(updated.notificationRecipients())
                .containsExactly("alerts@example.com", "owner@example.com");
    }

    @Test
    void validatesTimeZoneCurrencyLogoPrivacyAndRecipients() {
        WorkspaceSettings existing = settings();
        stubExisting(existing);

        assertInvalid(fullRequest("Not/AZone", "USD", null, null, null, null, List.of()));
        assertInvalid(fullRequest("UTC", "ZZZ", null, null, null, null, List.of()));
        assertInvalid(fullRequest("UTC", "CAD", null, null, null, null, List.of()));
        for (String path : List.of("https://example.com/logo.svg", "//example.com/logo.svg",
                "/assets/../secret", "/assets/%2e%2e/secret", "/assets\\logo.svg")) {
            assertInvalid(fullRequest("UTC", "USD", path, null, null, null, List.of()));
        }
        assertInvalid(fullRequest("UTC", "USD", null, "http://example.com/privacy",
                "Notice", null, List.of()));
        assertInvalid(fullRequest("UTC", "USD", null, "https://user@example.com/privacy",
                "Notice", null, List.of()));
        assertInvalid(fullRequest("UTC", "USD", null, "https://example.com/privacy",
                null, null, List.of()));
        assertInvalid(fullRequest("UTC", "USD", null, null, null, "v1", List.of()));
        assertInvalid(fullRequest("UTC", "USD", null, null, null, null,
                List.of("owner@example.com", " OWNER@EXAMPLE.COM ")));
        assertInvalid(fullRequest("UTC", "USD", null, null, null, null,
                List.of("invalid")));
        assertInvalid(fullRequest("UTC", "USD", null, null, null, null,
                java.util.stream.IntStream.range(0, 11).mapToObj(i -> "owner" + i + "@example.com").toList()));
    }

    @Test
    void acceptsExactlyTheSupportedCurrencySet() {
        for (String currency : List.of("USD", "EUR", "ILS", "JOD", "SAR", "AED", "GBP", "KWD", "QAR", "EGP")) {
            WorkspaceSettings existing = settings();
            stubExisting(existing);
            assertThat(service().update(fullRequest("UTC", currency, null, null, null, null, List.of())))
                    .extracting(response -> response.currency()).isEqualTo(currency);
            reset(repository, currentWorkspace);
        }
    }

    @Test
    void projectsOnlyEffectiveSafeAutomationFlags() {
        var response = service().getAutomationStatus();
        assertThat(response.dispatcherEnabled()).isTrue();
        assertThat(response.legacyCallbackEnabled()).isFalse();
        assertThat(response.retryEnabled()).isTrue();
        assertThat(response.attemptTrackingAvailable()).isTrue();
        verifyNoInteractions(repository);
    }

    @Test
    void initializationWithoutTrustedWorkspaceFailsClosed() {
        when(currentWorkspace.requireActive()).thenThrow(
                new org.springframework.security.access.AccessDeniedException("Access is denied"));

        assertThatThrownBy(() -> service().findWorkspace())
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("Access is denied");

        verify(repository, never()).initializeIfMissing(any(), any());
    }

    private WorkspaceSettingsService service() {
        return new WorkspaceSettingsService(repository, new QualificationReliabilityProperties(
                true, false, true, Duration.ofSeconds(5), 10, 5, Duration.ofSeconds(1),
                Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(30),
                Duration.ofMinutes(30), 50), currentWorkspace);
    }

    private WorkspaceSettings settings() {
        WorkspaceSettings settings = WorkspaceSettings.createNeutral(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(), (byte) 1);
        ReflectionTestUtils.setField(settings, "createdAt", Instant.parse("2026-08-14T00:00:00Z"));
        ReflectionTestUtils.setField(settings, "updatedAt", Instant.parse("2026-08-14T00:00:00Z"));
        return settings;
    }

    private void stubExisting(WorkspaceSettings settings) {
        when(currentWorkspace.requireActive()).thenReturn(settings.getWorkspace());
        when(repository.findByWorkspaceId(settings.getWorkspace().getId()))
                .thenReturn(Optional.of(settings));
    }

    private UpdateWorkspaceSettingsRequest request(long version, String name, String email, String description) {
        return new UpdateWorkspaceSettingsRequest(version, name, email, description,
                null, null, null, "UTC", "USD", "We usually respond within one business day.",
                null, null, null, List.of());
    }

    private UpdateWorkspaceSettingsRequest fullRequest(String timeZone, String currency, String logo,
            String privacyUrl, String privacyNotice, String privacyVersion, List<String> recipients) {
        return new UpdateWorkspaceSettingsRequest(0, "Workspace", null, null,
                null, null, logo, timeZone, currency, "We respond soon.", privacyUrl,
                privacyNotice, privacyVersion, recipients);
    }

    private void assertInvalid(UpdateWorkspaceSettingsRequest request) {
        assertThatThrownBy(() -> service().update(request))
                .isInstanceOf(WorkspaceSettingsService.InvalidWorkspaceSettingsException.class);
    }
}
