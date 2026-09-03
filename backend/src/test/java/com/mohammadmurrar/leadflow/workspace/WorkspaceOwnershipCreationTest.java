package com.mohammadmurrar.leadflow.workspace;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.notification.*;
import com.mohammadmurrar.leadflow.qualification.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceOwnershipCreationTest {
    @Test
    void immediateChildrenInheritTheExactParentWorkspace() {
        Workspace workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        Lead lead = lead(workspace);

        Notification notification = Notification.create(NotificationType.NEW_LEAD,
                NotificationSeverity.INFO, "New lead", "A new lead was received", lead);
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        QualificationDispatchOutbox outbox = QualificationDispatchOutbox.create(attempt, Instant.now());

        assertThat(notification.getWorkspace()).isSameAs(workspace);
        assertThat(attempt.getWorkspace()).isSameAs(workspace);
        assertThat(outbox.getWorkspace()).isSameAs(workspace);
    }

    @Test
    void notificationAndQualificationAttemptRejectLeadWithoutOwnership() {
        Lead unowned = Lead.create("Test Lead", "test@example.invalid", null, null,
                "Test service", BigDecimal.ZERO, LocalDate.now().plusDays(1),
                "A sufficiently detailed test inquiry message.", "test");

        assertThatThrownBy(() -> Notification.create(NotificationType.NEW_LEAD,
                NotificationSeverity.INFO, "New lead", "A new lead was received", unowned))
                .isInstanceOf(NullPointerException.class).hasMessage("Workspace is required");
        assertThatThrownBy(() -> QualificationAttempt.create(unowned, 1))
                .isInstanceOf(NullPointerException.class).hasMessage("Workspace is required");
    }

    @Test
    void qualificationOutboxRejectsAttemptWithoutOwnership() {
        QualificationAttempt unowned = mock(QualificationAttempt.class);

        assertThatThrownBy(() -> QualificationDispatchOutbox.create(unowned, Instant.now()))
                .isInstanceOf(NullPointerException.class).hasMessage("Workspace is required");
    }

    @Test
    void requestDtosExposeNoWritableWorkspaceIdentity() {
        Class<?>[] requestTypes = {
                com.mohammadmurrar.leadflow.lead.api.CreateLeadRequest.class,
                com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest.class,
                com.mohammadmurrar.leadflow.service.api.CreateServiceRequest.class,
                com.mohammadmurrar.leadflow.service.api.UpdateServiceRequest.class,
                com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest.class,
                com.mohammadmurrar.leadflow.passwordreset.api.PasswordResetRequest.class,
                com.mohammadmurrar.leadflow.passwordreset.api.PasswordResetConfirmationRequest.class
        };

        assertThat(Arrays.stream(requestTypes)
                .flatMap(type -> Arrays.stream(type.getRecordComponents()))
                .map(component -> component.getName().toLowerCase(java.util.Locale.ROOT)))
                .noneMatch(name -> name.equals("workspace") || name.equals("workspaceid")
                        || name.equals("workspace_id"));
    }

    private Lead lead(Workspace workspace) {
        Lead lead = Lead.create(workspace, "Test Lead", "test@example.invalid", null, null,
                "Test service", null, BigDecimal.ZERO, LocalDate.now().plusDays(1),
                "A sufficiently detailed test inquiry message.", "test");
        lead.startQualification();
        return lead;
    }
}
