package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.lead.api.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.*;
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
    @Spy QualificationReliabilityProperties reliabilityProperties = new QualificationReliabilityProperties(
            false, true, false, Duration.ofHours(1), 10, 3, Duration.ofSeconds(1), Duration.ofSeconds(10),
            Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofMinutes(30), 20);
    @InjectMocks LeadService service;

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void createsNotificationAndDurableAttemptInSameTransaction() {
        when(repository.existsByEmailAndCreatedAtAfter(eq("created@example.com"), any(Instant.class)))
                .thenReturn(false);
        when(repository.save(any(Lead.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeadResponse result = service.create(request("created@example.com"));

        ArgumentCaptor<Lead> savedLead = ArgumentCaptor.forClass(Lead.class);
        verify(repository).save(savedLead.capture());
        verify(notificationService).createNewLeadNotification(savedLead.getValue());
        verify(qualificationAttemptService).createInitialAttempt(savedLead.getValue());
        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFYING);
    }

    @Test
    void rejectsNormalizedDuplicateWithoutCreatingDownstreamRecords() {
        when(repository.existsByEmailAndCreatedAtAfter(eq("duplicate@example.com"), any(Instant.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request("  DUPLICATE@EXAMPLE.COM  ")))
                .isInstanceOf(DuplicateLeadException.class)
                .hasMessage("A recent lead already exists for this email")
                .hasMessageNotContaining("duplicate@example.com");
        verify(repository).existsByEmailAndCreatedAtAfter(eq("duplicate@example.com"), any(Instant.class));
        verify(repository, never()).save(any());
        verifyNoInteractions(notificationService, qualificationAttemptService);
    }

    @Test
    void firstQualificationCreatesExactlyOneNotification() {
        Lead lead = lead();
        when(repository.findById(lead.getId())).thenReturn(Optional.of(lead));

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
        when(repository.findById(lead.getId())).thenReturn(Optional.of(lead));

        LeadResponse result = service.qualify(lead.getId(), qualification(42));

        assertThat(result.status()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(result.qualificationScore()).isEqualTo(88);
        assertThat(result.aiSummary()).isEqualTo("Original qualification summary.");
        verifyNoInteractions(notificationService);
    }

    private Lead lead() {
        Lead lead = Lead.create("Alex Morgan", "lead@example.com", "+1 555 0100",
                "Northstar Services", "Backend API", new BigDecimal("4500.00"),
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
