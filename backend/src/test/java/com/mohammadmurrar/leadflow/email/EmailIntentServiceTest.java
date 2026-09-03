package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.qualification.QualificationAttempt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class EmailIntentServiceTest {
    @Test
    void snapshotsNormalizedUniqueRecipientsInDeterministicOrder() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        InquiryEmailRecipientResolver recipients = mock(InquiryEmailRecipientResolver.class);
        when(recipients.resolveNewInquiryRecipients(any())).thenReturn(List.of(
                "alpha@example.invalid", "zeta@example.invalid"));
        EmailIntentService service = new EmailIntentService(outbox, recipients,
                new EmailIntentKeyFactory());
        Lead lead = lead();

        service.enqueueNewLead(lead);

        ArgumentCaptor<String> capturedRecipients = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(2)).enqueue(eq(EmailTemplateType.NEW_INQUIRY), capturedRecipients.capture(),
                same(lead), keys.capture(), any(Instant.class));
        assertThat(capturedRecipients.getAllValues()).containsExactly(
                "alpha@example.invalid", "zeta@example.invalid");
        assertThat(keys.getAllValues()).hasSize(2).doesNotHaveDuplicates()
                .allSatisfy(key -> assertThat(key).startsWith("new-lead:")
                        .doesNotContain("@", "example.invalid"));
    }

    @Test
    void noEligibleRecipientsCreatesNoRowsAndLogsOnlyFixedSafeMessage(CapturedOutput output) {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        InquiryEmailRecipientResolver recipients = mock(InquiryEmailRecipientResolver.class);
        when(recipients.resolveNewInquiryRecipients(any())).thenReturn(List.of());

        new EmailIntentService(outbox, recipients, new EmailIntentKeyFactory()).enqueueNewLead(lead());

        verifyNoInteractions(outbox);
        assertThat(output).contains(
                "Inquiry email notification suppressed because no eligible recipient is configured")
                .doesNotContain("forbidden-email@example.invalid", "Distinctive Lead",
                        "Distinctive Company", "Forbidden inquiry message");
    }

    @Test
    void attemptIdentityAndEventTypeProduceDistinctIntents() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        InquiryEmailRecipientResolver recipients = mock(InquiryEmailRecipientResolver.class);
        when(recipients.resolveExplicitRecipients(any())).thenReturn(List.of("recipient@example.invalid"));
        EmailIntentService service = new EmailIntentService(outbox, recipients, new EmailIntentKeyFactory());
        Lead lead = lead();
        QualificationAttempt first = QualificationAttempt.create(lead, 1);
        QualificationAttempt second = QualificationAttempt.create(lead, 2);

        service.enqueueQualificationFailure(lead, first);
        service.enqueueQualificationSuccess(lead, second);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(outbox, times(2)).enqueue(any(), eq("recipient@example.invalid"), same(lead),
                keys.capture(), any(Instant.class));
        assertThat(keys.getAllValues()).hasSize(2).doesNotHaveDuplicates();
        assertThat(keys.getAllValues().get(0)).startsWith("qualification-failure:");
        assertThat(keys.getAllValues().get(1)).startsWith("qualification-success:");
    }

    private Lead lead() {
        Lead lead = Lead.create(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA(),
                "Distinctive Lead", "forbidden-email@example.invalid",
                "forbidden-phone", "Distinctive Company", "Distinctive Service",
                null,
                new BigDecimal("9999.99"), LocalDate.now().plusDays(3),
                "Forbidden inquiry message", "forbidden-source");
        lead.startQualification();
        return lead;
    }
}
