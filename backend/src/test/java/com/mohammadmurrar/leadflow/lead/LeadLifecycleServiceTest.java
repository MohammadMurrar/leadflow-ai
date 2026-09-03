package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@ExtendWith(MockitoExtension.class)
class LeadLifecycleServiceTest {
    @Mock LeadRepository repository;
    @Mock LeadQualificationWebhookClient webhookClient;
    @Mock NotificationService notificationService;
    @Mock QualificationAttemptService qualificationAttemptService;
    @Mock ServiceOfferingService serviceOfferingService;
    @Mock CurrentWorkspace currentWorkspace;
    private final Workspace workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();

    @Test
    void allowsEveryDeclaredTransitionAndPreservesQualificationData() {
        for (Transition transition : new Transition[]{
                new Transition(LeadStatus.QUALIFIED, LeadStatus.CONTACTED),
                new Transition(LeadStatus.QUALIFIED, LeadStatus.WON),
                new Transition(LeadStatus.QUALIFIED, LeadStatus.LOST),
                new Transition(LeadStatus.CONTACTED, LeadStatus.WON),
                new Transition(LeadStatus.CONTACTED, LeadStatus.LOST)}) {
            Lead lead = leadAt(transition.from());
            when(currentWorkspace.requireActiveId()).thenReturn(workspace.getId());
            when(repository.findByIdAndWorkspaceId(lead.getId(), workspace.getId())).thenReturn(Optional.of(lead));
            LeadResponse result = service().changeStatus(lead.getId(), transition.to(), lead.getVersion());
            assertThat(result.status()).isEqualTo(transition.to());
            assertQualificationIntegrity(result);
            verify(repository).flush();
            clearInvocations(repository);
        }
        verifyNoInteractions(webhookClient, notificationService);
    }

    @Test
    void rejectsEveryUnsupportedSourceAndTargetCombination() {
        for (LeadStatus from : LeadStatus.values()) {
            for (LeadStatus to : LeadStatus.values()) {
                if (from == to || allowed(from, to)) continue;
                Lead lead = leadAt(from);
                when(currentWorkspace.requireActiveId()).thenReturn(workspace.getId());
                when(repository.findByIdAndWorkspaceId(lead.getId(), workspace.getId())).thenReturn(Optional.of(lead));
                assertThatThrownBy(() -> service().changeStatus(lead.getId(), to, lead.getVersion()))
                        .isInstanceOf(ConflictException.class)
                        .hasMessageContaining("cannot transition");
                verify(repository, never()).flush();
                clearInvocations(repository);
            }
        }
        verifyNoInteractions(webhookClient, notificationService);
    }

    @Test
    void currentStatusIsIdempotentWithoutFlushOrSideEffects() {
        Lead lead = leadAt(LeadStatus.CONTACTED);
        when(currentWorkspace.requireActiveId()).thenReturn(workspace.getId());
        when(repository.findByIdAndWorkspaceId(lead.getId(), workspace.getId())).thenReturn(Optional.of(lead));
        LeadResponse first = service().changeStatus(lead.getId(), LeadStatus.CONTACTED, lead.getVersion());
        LeadResponse second = service().changeStatus(lead.getId(), LeadStatus.CONTACTED, lead.getVersion());
        assertThat(first).isEqualTo(second);
        verify(repository, never()).flush();
        verifyNoInteractions(webhookClient, notificationService);
    }

    @Test
    void rejectsStaleVersionAndUnknownLead() {
        Lead lead = leadAt(LeadStatus.QUALIFIED);
        when(currentWorkspace.requireActiveId()).thenReturn(workspace.getId());
        when(repository.findByIdAndWorkspaceId(lead.getId(), workspace.getId())).thenReturn(Optional.of(lead));
        assertThatThrownBy(() -> service().changeStatus(lead.getId(), LeadStatus.CONTACTED,
                lead.getVersion() + 1)).isInstanceOf(ConflictException.class)
                .hasMessage("Lead changed elsewhere. Refresh and try again");
        UUID unknown = UUID.randomUUID();
        when(repository.findByIdAndWorkspaceId(unknown, workspace.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().changeStatus(unknown, LeadStatus.CONTACTED, 0))
                .isInstanceOf(NotFoundException.class);
        verify(repository, never()).flush();
    }

    @Test
    void qualificationRetryDoesNotRegressAdvancedLifecycleStatus() {
        Lead lead = leadAt(LeadStatus.WON);
        when(repository.findActiveByIdForAutomation(lead.getId())).thenReturn(Optional.of(lead));
        LeadResponse result = service().qualify(lead.getId(), new com.mohammadmurrar.leadflow.lead.api.QualificationRequest(
                10, LeadPriority.LOW, "Changed", "Changed summary", "Changed reply"));
        assertThat(result.status()).isEqualTo(LeadStatus.WON);
        assertQualificationIntegrity(result);
        verifyNoInteractions(notificationService, webhookClient);
    }

    private LeadService service() {
        return new LeadService(repository, notificationService, qualificationAttemptService,
                new QualificationReliabilityProperties(false, true, false,
                        java.time.Duration.ofHours(1), 10, 3, java.time.Duration.ofSeconds(1),
                        java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(1),
                        java.time.Duration.ofMinutes(30), java.time.Duration.ofMinutes(30), 20),
                serviceOfferingService,
                mock(com.mohammadmurrar.leadflow.email.EmailIntentService.class),
                currentWorkspace);
    }

    private Lead leadAt(LeadStatus status) {
        Lead lead = Lead.create(workspace, "Lifecycle Lead", "lifecycle@example.com", "+1 555 0100",
                "Lifecycle Co", "Sales workflow", null, new BigDecimal("5000.00"),
                LocalDate.parse("2026-09-01"), "A detailed lifecycle test request.", "test");
        if (status == LeadStatus.NEW) return lead;
        lead.startQualification();
        if (status == LeadStatus.QUALIFYING) return lead;
        if (status == LeadStatus.AUTOMATION_FAILED) {
            ReflectionTestUtils.setField(lead, "status", LeadStatus.AUTOMATION_FAILED);
            return lead;
        }
        lead.applyQualification(91, LeadPriority.HIGH, "Enterprise",
                "Original AI summary", "Original recommended reply");
        if (status != LeadStatus.QUALIFIED) lead.transitionTo(status);
        return lead;
    }

    private boolean allowed(LeadStatus from, LeadStatus to) {
        return from == LeadStatus.QUALIFIED && (to == LeadStatus.CONTACTED || to == LeadStatus.WON || to == LeadStatus.LOST)
                || from == LeadStatus.CONTACTED && (to == LeadStatus.WON || to == LeadStatus.LOST);
    }

    private void assertQualificationIntegrity(LeadResponse result) {
        assertThat(result.qualificationScore()).isEqualTo(91);
        assertThat(result.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(result.category()).isEqualTo("Enterprise");
        assertThat(result.aiSummary()).isEqualTo("Original AI summary");
        assertThat(result.recommendedReply()).isEqualTo("Original recommended reply");
    }

    private record Transition(LeadStatus from, LeadStatus to) {}
}
