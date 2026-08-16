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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceSettingsServiceTest {
    @Mock WorkspaceSettingsRepository repository;

    @Test
    void initializesMissingWorkspaceWithoutOverwritingAnExistingSingleton() {
        WorkspaceSettings existing = settings();
        when(repository.findBySingletonKey((byte) 1)).thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        var response = service().findWorkspace();
        verify(repository).initializeIfMissing(any());
        assertThat(response.workspaceName()).isEqualTo("My Workspace");
    }

    @Test
    void trimsUpdatesAndLeavesNoOpVersionAndTimestampUntouched() {
        WorkspaceSettings existing = settings();
        when(repository.findBySingletonKey((byte) 1)).thenReturn(Optional.of(existing));
        var updated = service().update(new UpdateWorkspaceSettingsRequest(0,
                "  Revenue   Operations  ", "  ops@example.com  ", "  Qualified leads  "));
        assertThat(updated.workspaceName()).isEqualTo("Revenue Operations");
        assertThat(updated.contactEmail()).isEqualTo("ops@example.com");
        assertThat(updated.description()).isEqualTo("Qualified leads");
        verify(repository).flush();

        reset(repository);
        when(repository.findBySingletonKey((byte) 1)).thenReturn(Optional.of(existing));
        service().update(new UpdateWorkspaceSettingsRequest(0,
                "Revenue Operations", "ops@example.com", "Qualified leads"));
        verify(repository, never()).flush();
    }

    @Test
    void rejectsInvalidEmailDescriptionAndStaleVersion() {
        WorkspaceSettings existing = settings();
        when(repository.findBySingletonKey((byte) 1)).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> service().update(new UpdateWorkspaceSettingsRequest(0,
                "Workspace", "not-email", null)))
                .isInstanceOf(WorkspaceSettingsService.InvalidWorkspaceSettingsException.class);
        assertThatThrownBy(() -> service().update(new UpdateWorkspaceSettingsRequest(0,
                "Workspace", null, "x".repeat(501))))
                .isInstanceOf(WorkspaceSettingsService.InvalidWorkspaceSettingsException.class);
        assertThatThrownBy(() -> service().update(new UpdateWorkspaceSettingsRequest(4,
                "Workspace", null, null))).isInstanceOf(ConflictException.class);
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

    private WorkspaceSettingsService service() {
        return new WorkspaceSettingsService(repository, new QualificationReliabilityProperties(
                true, false, true, Duration.ofSeconds(5), 10, 5, Duration.ofSeconds(1),
                Duration.ofMinutes(1), Duration.ofMinutes(1), Duration.ofMinutes(30),
                Duration.ofMinutes(30), 50));
    }

    private WorkspaceSettings settings() {
        WorkspaceSettings settings = WorkspaceSettings.createNeutral();
        ReflectionTestUtils.setField(settings, "createdAt", Instant.parse("2026-08-14T00:00:00Z"));
        ReflectionTestUtils.setField(settings, "updatedAt", Instant.parse("2026-08-14T00:00:00Z"));
        return settings;
    }
}
