package com.mohammadmurrar.leadflow.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EmailDeliverySchedulerTest {
    @Test
    void infrastructureFailureIsContainedWithoutSendingAndLaterCycleStillRuns() {
        EmailOutboxService outbox = mock(EmailOutboxService.class);
        EmailSender sender = mock(EmailSender.class);
        EmailDeliveryProperties properties = EmailTemplateRendererTest.properties(
                true, "https://app.example.invalid");
        EmailDispatchService dispatcher = new EmailDispatchService(outbox,
                new EmailTemplateRenderer(properties), sender, properties);
        EmailDeliveryScheduler scheduler = new EmailDeliveryScheduler(dispatcher);
        RuntimeException failure = new RuntimeException("credential=distinctive-sensitive-value");
        when(outbox.claimAvailable()).thenThrow(failure).thenReturn(List.of());
        Logger logger = (Logger) LoggerFactory.getLogger(EmailDeliveryScheduler.class);
        ListAppender<ILoggingEvent> events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
        try {
            assertThatCode(scheduler::dispatch).doesNotThrowAnyException();
            assertThat(events.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getFormattedMessage()).isEqualTo("Email dispatch cycle could not be completed");
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getArgumentArray()).isNullOrEmpty();
            });
            String captured = events.list.toString();
            assertThat(captured)
                    .doesNotContain("distinctive-sensitive-value", "RuntimeException", "at ",
                            "recipient@example.invalid", "Sensitive body", "outbox-id", "lease-token");
            verifyNoInteractions(sender);

            assertThatCode(scheduler::dispatch).doesNotThrowAnyException();
            verify(outbox, times(2)).claimAvailable();
            verifyNoInteractions(sender);
            assertThat(events.list).hasSize(1);
        } finally {
            logger.detachAppender(events);
            events.stop();
        }
    }
}
