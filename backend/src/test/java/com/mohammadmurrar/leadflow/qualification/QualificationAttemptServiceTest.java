package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.email.EmailIntentService;
import com.mohammadmurrar.leadflow.qualification.api.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

@ExtendWith(MockitoExtension.class)
class QualificationAttemptServiceTest {
    @Mock LeadRepository leads;
    @Mock QualificationAttemptRepository attempts;
    @Mock QualificationDispatchOutboxRepository outbox;
    @Mock NotificationService notifications;
    @Mock EmailIntentService emailIntents;
    @Mock CurrentWorkspace currentWorkspace;

    @Test
    void initialAttemptStartsAtOneAndCreatesDurableOutbox() {
        Lead lead = lead();
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        QualificationAttempt attempt = service(false).createInitialAttempt(lead);
        assertThat(attempt.getAttemptNumber()).isOne();
        assertThat(attempt.getStatus()).isEqualTo(QualificationAttemptStatus.PENDING);
        verify(outbox).save(any(QualificationDispatchOutbox.class));
    }

    @Test
    void startIsIdempotentAndSuccessAppliesExactlyOnce() {
        Lead lead = lead();
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        stubLocked(lead, attempt);
        QualificationAttemptService service = service(false);
        assertThat(service.start(lead.getId(), attempt.getId(), new QualificationStartRequest("execution-1")).accepted()).isTrue();
        assertThat(service.start(lead.getId(), attempt.getId(), new QualificationStartRequest("execution-1")).accepted()).isFalse();
        QualificationSuccessRequest request = success();
        service.succeed(lead.getId(), attempt.getId(), request);
        service.succeed(lead.getId(), attempt.getId(), request);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(attempt.getStatus()).isEqualTo(QualificationAttemptStatus.SUCCEEDED);
        verify(notifications, times(1)).createQualificationNotification(lead);
        verify(emailIntents, times(1)).enqueueQualificationSuccess(lead, attempt);
    }

    @Test
    void contradictoryTerminalCallbackConflicts() {
        Lead lead = lead();
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        stubLocked(lead, attempt);
        QualificationAttemptService service = service(false);
        service.start(lead.getId(), attempt.getId(), new QualificationStartRequest("execution-1"));
        service.succeed(lead.getId(), attempt.getId(), success());
        assertThatThrownBy(() -> service.fail(lead.getId(), attempt.getId(), failure()))
                .isInstanceOf(ConflictException.class);
        verify(notifications, never()).createAutomationFailedNotification(any());
    }

    @Test
    void failureAndTimeoutEachProduceOneTerminalNotification() {
        Lead failedLead = lead();
        QualificationAttempt failed = QualificationAttempt.create(failedLead, 1);
        stubLocked(failedLead, failed);
        QualificationAttemptService service = service(false);
        service.start(failedLead.getId(), failed.getId(), new QualificationStartRequest("execution-1"));
        service.fail(failedLead.getId(), failed.getId(), failure());
        service.fail(failedLead.getId(), failed.getId(), failure());
        verify(notifications, times(1)).createAutomationFailedNotification(failedLead);
        verify(emailIntents, times(1)).enqueueQualificationFailure(failedLead, failed);

        Lead timedOutLead = lead();
        QualificationAttempt timedOut = QualificationAttempt.create(timedOutLead, 1);
        when(attempts.findActiveByIdForUpdate(timedOut.getId())).thenReturn(Optional.of(timedOut));
        when(leads.findByIdAndWorkspaceIdForUpdate(
                timedOutLead.getId(), timedOutLead.getWorkspace().getId()))
                .thenReturn(Optional.of(timedOutLead));
        when(outbox.existsConsistentByAttemptIdAndWorkspaceId(
                timedOut.getId(), timedOutLead.getWorkspace().getId())).thenReturn(true);
        service.timeOut(timedOut.getId());
        service.timeOut(timedOut.getId());
        assertThat(timedOut.getStatus()).isEqualTo(QualificationAttemptStatus.TIMED_OUT);
        verify(notifications, times(1)).createAutomationFailedNotification(timedOutLead);
        verify(emailIntents, times(1)).enqueueQualificationFailure(timedOutLead, timedOut);
    }

    @Test
    void exhaustedDeliveryProducesOneTerminalFailureIntent() {
        Lead lead = lead();
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        when(attempts.findActiveByIdAndWorkspaceIdForUpdate(
                attempt.getId(), lead.getWorkspace().getId())).thenReturn(Optional.of(attempt));
        when(leads.findByIdAndWorkspaceIdForUpdate(
                lead.getId(), lead.getWorkspace().getId())).thenReturn(Optional.of(lead));
        when(outbox.existsConsistentByAttemptIdAndWorkspaceId(
                attempt.getId(), lead.getWorkspace().getId())).thenReturn(true);
        QualificationAttemptService service = service(false);

        assertThat(service.failKnownDelivery(attempt.getId(), lead.getWorkspace().getId())).isTrue();
        assertThat(service.failKnownDelivery(attempt.getId(), lead.getWorkspace().getId())).isFalse();

        assertThat(lead.getStatus()).isEqualTo(LeadStatus.AUTOMATION_FAILED);
        assertThat(attempt.getStatus()).isEqualTo(QualificationAttemptStatus.FAILED);
        verify(notifications).createAutomationFailedNotification(lead);
        verify(emailIntents).enqueueQualificationFailure(lead, attempt);
    }

    @Test
    void inactiveOrMismatchedCallbackFailsClosedWithoutMutation() {
        Lead lead = lead();
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        when(attempts.findActiveByIdForUpdate(attempt.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(false).start(lead.getId(), attempt.getId(),
                new QualificationStartRequest("execution-1")))
                .isInstanceOf(com.mohammadmurrar.leadflow.common.NotFoundException.class)
                .hasMessage("Qualification attempt not found");
        assertThat(attempt.getStatus()).isEqualTo(QualificationAttemptStatus.PENDING);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUALIFYING);
        verifyNoInteractions(notifications, emailIntents);
    }

    @Test
    void retryIsGatedAndCreatesOneNewHistoricalAttempt() {
        Lead lead = lead();
        ReflectionTestUtils.setField(lead, "status", LeadStatus.AUTOMATION_FAILED);
        QualificationAttempt previous = QualificationAttempt.create(lead, 1);
        previous.fail(QualificationFailureCode.UNKNOWN, "Automated qualification did not complete.", null, Instant.now());
        when(currentWorkspace.requireActiveId()).thenReturn(lead.getWorkspace().getId());
        when(leads.findByIdAndWorkspaceIdForUpdate(lead.getId(), lead.getWorkspace().getId()))
                .thenReturn(Optional.of(lead));
        when(attempts.findActiveByLeadAndWorkspace(
                eq(lead.getId()), eq(lead.getWorkspace().getId()), anyList())).thenReturn(Optional.empty());
        when(attempts.findLatestByLeadAndWorkspace(
                lead.getId(), lead.getWorkspace().getId())).thenReturn(Optional.of(previous));
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        QualificationOutcomeResponse outcome = service(true).retry(lead.getId(), lead.getVersion());
        assertThat(outcome.attempt().attemptNumber()).isEqualTo(2);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUALIFYING);
        verify(outbox).save(any());
        verifyNoInteractions(emailIntents);
        assertThatThrownBy(() -> service(false).retry(lead.getId(), lead.getVersion()))
                .isInstanceOf(ConflictException.class).hasMessage("Qualification retry is not enabled");
    }

    private void stubLocked(Lead lead, QualificationAttempt attempt) {
        when(attempts.findActiveByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
        when(outbox.existsConsistentByAttemptIdAndWorkspaceId(
                attempt.getId(), lead.getWorkspace().getId())).thenReturn(true);
        when(leads.findByIdAndWorkspaceIdForUpdate(
                lead.getId(), lead.getWorkspace().getId())).thenReturn(Optional.of(lead));
    }

    private QualificationAttemptService service(boolean retry) {
        QualificationReliabilityProperties properties = new QualificationReliabilityProperties(false, true, retry,
                Duration.ofHours(1), 10, 3, Duration.ofSeconds(1), Duration.ofSeconds(10),
                Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofMinutes(30), 20);
        return new QualificationAttemptService(leads, attempts, outbox, notifications, properties,
                emailIntents, currentWorkspace);
    }

    private Lead lead() {
        Lead lead = Lead.create(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "Reliability Lead", "reliability@example.com", null, "Reliable Co",
                "AI automation", null, new BigDecimal("5000"), LocalDate.now().plusDays(7),
                "A detailed request for reliable automation.", "test");
        lead.startQualification();
        return lead;
    }

    private QualificationSuccessRequest success() {
        return new QualificationSuccessRequest(88, LeadPriority.HIGH, "AI_AUTOMATION",
                "Qualified automation lead.", "Let us schedule a discovery call.", "execution-1");
    }

    private QualificationFailureRequest failure() {
        return new QualificationFailureRequest(QualificationFailureCode.AI_PROVIDER_ERROR,
                "Automated qualification did not complete.", "execution-1");
    }
}
