package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.lead.api.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.email.EmailIntentService;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeadServiceNotificationTest {
    @Mock LeadRepository repository;
    @Mock NotificationService notificationService;
    @Mock QualificationAttemptService qualificationAttemptService;
    @Mock ServiceOfferingService serviceOfferingService;
    @Mock EmailIntentService emailIntentService;
    @Mock CurrentWorkspace currentWorkspace;
    @Spy QualificationReliabilityProperties reliabilityProperties = new QualificationReliabilityProperties(
            false, true, false, Duration.ofHours(1), 10, 3, Duration.ofSeconds(1), Duration.ofSeconds(10),
            Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofMinutes(30), 20);
    @InjectMocks LeadService service;
    private Workspace workspace;

    @BeforeEach
    void setUpWorkspace() {
        workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        lenient().when(currentWorkspace.requireActive()).thenReturn(workspace);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsNotificationAndDurableAttemptInSameTransaction() {
        when(repository.existsByWorkspaceIdAndEmailAndCreatedAtAfter(eq(workspace.getId()),
                eq("created@example.com"), any(Instant.class)))
                .thenReturn(false);
        when(repository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse result = service.create(request("created@example.com"));

        ArgumentCaptor<Lead> savedLead = ArgumentCaptor.forClass(Lead.class);
        verify(repository).save(savedLead.capture());
        verify(notificationService).createNewLeadNotification(savedLead.getValue());
        verify(qualificationAttemptService).createInitialAttempt(savedLead.getValue());
        verify(repository).flush();
        verify(emailIntentService).enqueueNewLead(savedLead.getValue());
        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFYING);
        assertThat(savedLead.getValue().getWorkspace()).isSameAs(workspace);
        verify(currentWorkspace).requireActive();
    }

    @Test
    void rejectsNormalizedDuplicateWithoutCreatingDownstreamRecords() {
        when(repository.existsByWorkspaceIdAndEmailAndCreatedAtAfter(eq(workspace.getId()),
                eq("duplicate@example.com"), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request("  DUPLICATE@EXAMPLE.COM  ")))
                .isInstanceOf(DuplicateLeadException.class)
                .hasMessage("A recent lead already exists for this email")
                .hasMessageNotContaining("duplicate@example.com");
        verify(repository).existsByWorkspaceIdAndEmailAndCreatedAtAfter(eq(workspace.getId()),
                eq("duplicate@example.com"), any(Instant.class));
        verify(repository, never()).save(any());
        verifyNoInteractions(emailIntentService);
        verifyNoInteractions(notificationService, qualificationAttemptService);
    }

    @Test
    void authenticatedCreationWithoutCurrentWorkspaceFailsClosed() {
        reset(currentWorkspace);
        when(currentWorkspace.requireActive()).thenThrow(
                new org.springframework.security.access.AccessDeniedException("Access is denied"));

        assertThatThrownBy(() -> service.create(request("created@example.com")))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessage("Access is denied");

        verifyNoInteractions(repository, notificationService, qualificationAttemptService,
                serviceOfferingService, emailIntentService);
    }

    @Test
    void workspaceCannotCreateLeadUsingAnotherWorkspacesService() {
        java.util.UUID foreignServiceId = java.util.UUID.randomUUID();
        when(repository.existsByWorkspaceIdAndEmailAndCreatedAtAfter(eq(workspace.getId()),
                eq("foreign-service@example.com"), any(Instant.class))).thenReturn(false);
        when(serviceOfferingService.getActive(workspace, foreignServiceId))
                .thenThrow(new com.mohammadmurrar.leadflow.common.NotFoundException("Service not found"));

        assertThatThrownBy(() -> service.create(new CreateLeadRequest("Alex Morgan",
                "foreign-service@example.com", null, null, foreignServiceId, null,
                null, null, "A sufficiently detailed cross-workspace request message.", "test")))
                .isInstanceOf(com.mohammadmurrar.leadflow.common.NotFoundException.class);

        verify(repository, never()).save(any());
        verifyNoInteractions(notificationService, qualificationAttemptService, emailIntentService);
    }

    @Test
    void firstQualificationCreatesExactlyOneNotification() {
        Lead lead = lead();
        when(repository.findActiveByIdForAutomation(lead.getId())).thenReturn(Optional.of(lead));

        LeadResponse result = service.qualify(lead.getId(), qualification(88));

        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(result.qualificationScore()).isEqualTo(88);
        assertThat(result.priority()).isEqualTo(LeadPriority.HIGH);
        verify(notificationService).createQualificationNotification(lead);
    }

    @Test
    void qualificationRetryReturnsCurrentLeadWithoutChangesOrNotification() {
        Lead lead = lead();
        lead.applyQualification(88, LeadPriority.HIGH, "Backend Development",
                "Original qualification summary.", "Original recommended reply.");
        when(repository.findActiveByIdForAutomation(lead.getId())).thenReturn(Optional.of(lead));

        LeadResponse result = service.qualify(lead.getId(), qualification(42));

        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(result.qualificationScore()).isEqualTo(88);
        assertThat(result.aiSummary()).isEqualTo("Original qualification summary.");
        verifyNoInteractions(notificationService);
    }

    private Lead lead() {
        Lead lead = Lead.create(workspace, "Alex Morgan", "lead@example.com", "+1 555 0100",
                "Northstar Services", "Backend API", null, new BigDecimal("4500.00"),
                LocalDate.now().plusDays(14),
                "We need a secure customer portal API integrated with our CRM.", "test");
        lead.startQualification();
        return lead;
    }

    private CreateLeadRequest request(String email) {
        return new CreateLeadRequest("Alex Morgan", email, "+1 555 0100", "Northstar Services",
                "Backend API", new BigDecimal("4500.00"), LocalDate.now().plusDays(14),
                "We need a secure customer portal API integrated with our CRM.", "test");
    }

    private QualificationRequest qualification(int score) {
        return new QualificationRequest(score, LeadPriority.HIGH, "Backend Development",
                "Qualified lead with a defined integration requirement.",
                "Thanks for sharing your requirements. Let us schedule a discovery call.");
    }
}
