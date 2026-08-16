package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
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

@ExtendWith(MockitoExtension.class)
class QualificationAttemptServiceTest {
    @Mock LeadRepository leads;
    @Mock QualificationAttemptRepository attempts;
    @Mock QualificationDispatchOutboxRepository outbox;
    @Mock NotificationService notifications;

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

        Lead timedOutLead = lead();
        QualificationAttempt timedOut = QualificationAttempt.create(timedOutLead, 1);
        when(attempts.findByIdForUpdate(timedOut.getId())).thenReturn(Optional.of(timedOut));
        when(leads.findByIdForUpdate(timedOutLead.getId())).thenReturn(Optional.of(timedOutLead));
        service.timeOut(timedOut.getId());
        service.timeOut(timedOut.getId());
        assertThat(timedOut.getStatus()).isEqualTo(QualificationAttemptStatus.TIMED_OUT);
        verify(notifications, times(1)).createAutomationFailedNotification(timedOutLead);
    }

    @Test
    void retryIsGatedAndCreatesOneNewHistoricalAttempt() {
        Lead lead = lead();
        ReflectionTestUtils.setField(lead, "status", LeadStatus.AUTOMATION_FAILED);
        QualificationAttempt previous = QualificationAttempt.create(lead, 1);
        previous.fail(QualificationFailureCode.UNKNOWN, "Automated qualification did not complete.", null, Instant.now());
        when(leads.findByIdForUpdate(lead.getId())).thenReturn(Optional.of(lead));
        when(attempts.findByLeadIdAndStatusIn(eq(lead.getId()), anyList())).thenReturn(Optional.empty());
        when(attempts.findFirstByLeadIdOrderByAttemptNumberDesc(lead.getId())).thenReturn(Optional.of(previous));
        when(attempts.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        QualificationOutcomeResponse outcome = service(true).retry(lead.getId(), lead.getVersion());
        assertThat(outcome.attempt().attemptNumber()).isEqualTo(2);
        assertThat(lead.getStatus()).isEqualTo(LeadStatus.QUALIFYING);
        verify(outbox).save(any());
        assertThatThrownBy(() -> service(false).retry(lead.getId(), lead.getVersion()))
                .isInstanceOf(ConflictException.class).hasMessage("Qualification retry is not enabled");
    }

    private void stubLocked(Lead lead, QualificationAttempt attempt) {
        when(leads.findByIdForUpdate(lead.getId())).thenReturn(Optional.of(lead));
        when(attempts.findByIdForUpdate(attempt.getId())).thenReturn(Optional.of(attempt));
    }

    private QualificationAttemptService service(boolean retry) {
        QualificationReliabilityProperties properties = new QualificationReliabilityProperties(false, true, retry,
                Duration.ofHours(1), 10, 3, Duration.ofSeconds(1), Duration.ofSeconds(10),
                Duration.ofMinutes(1), Duration.ofMinutes(30), Duration.ofMinutes(30), 20);
        return new QualificationAttemptService(leads, attempts, outbox, notifications, properties);
    }

    private Lead lead() {
        Lead lead = Lead.create("Reliability Lead", "reliability@example.com", null, "Reliable Co",
                "AI automation", new BigDecimal("5000"), LocalDate.now().plusDays(7),
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
