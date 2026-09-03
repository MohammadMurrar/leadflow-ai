package com.mohammadmurrar.leadflow.email;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class EmailOutboxTest {
    @Test
    void lifecycleRequiresMatchingLeaseAndRecoversExpiredClaims() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", null, "new-inquiry:one", now);

        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getDeliveryCount()).isZero();
        assertThat(outbox.claim("lease-one", now, now.plusSeconds(30))).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.IN_PROGRESS);
        assertThat(outbox.getDeliveryCount()).isOne();
        assertThat(outbox.delivered("wrong-lease", now)).isFalse();
        assertThat(outbox.reschedule("lease-one", EmailFailureCode.CONNECTION, now.plusSeconds(10))).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(outbox.getFailureCode()).isEqualTo(EmailFailureCode.CONNECTION);

        assertThat(outbox.claim("too-early", now.plusSeconds(9), now.plusSeconds(40))).isFalse();
        assertThat(outbox.claim("lease-two", now.plusSeconds(10), now.plusSeconds(20))).isTrue();
        assertThat(outbox.getDeliveryCount()).isEqualTo(2);
        assertThat(outbox.claim("lease-three", now.plusSeconds(20), now.plusSeconds(50))).isTrue();
        assertThat(outbox.getDeliveryCount()).isEqualTo(3);
        assertThat(outbox.delivered("lease-two", now.plusSeconds(21))).isFalse();
        assertThat(outbox.reschedule("lease-two", EmailFailureCode.CONNECTION,
                now.plusSeconds(30))).isFalse();
        assertThat(outbox.delivered("lease-three", now.plusSeconds(21))).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.DELIVERED);
        assertThat(outbox.claim("again", now.plusSeconds(60), now.plusSeconds(90))).isFalse();
    }

    @Test
    void maximumAttemptBoundaryDoesNotCreateAnAdditionalClaim() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", null, "event:attempt-boundary", now);
        assertThat(outbox.exhaustWithoutClaimIfAttemptsReached(2, now)).isFalse();
        assertThat(outbox.claim("lease-one", now, now.plusSeconds(1))).isTrue();
        assertThat(outbox.reschedule("lease-one", EmailFailureCode.CONNECTION, now)).isTrue();
        assertThat(outbox.exhaustWithoutClaimIfAttemptsReached(2, now)).isFalse();
        assertThat(outbox.claim("lease-two", now, now.plusSeconds(1))).isTrue();
        assertThat(outbox.getDeliveryCount()).isEqualTo(2);
        assertThat(outbox.exhaustWithoutClaimIfAttemptsReached(2, now.plusSeconds(1))).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(outbox.claim("lease-three", now.plusSeconds(2), now.plusSeconds(3))).isFalse();
        assertThat(outbox.getDeliveryCount()).isEqualTo(2);
    }

    @Test
    void renewalProvidesFreshFullLeaseWithoutIncrementingDeliveryCount() {
        Instant claimedAt = Instant.parse("2026-08-24T10:00:00Z");
        Instant sendStart = claimedAt.plusSeconds(25);
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.NEW_INQUIRY,
                "recipient@example.invalid", null, "event:later-batch-item", claimedAt);
        assertThat(outbox.claim("lease", claimedAt, claimedAt.plusSeconds(30))).isTrue();

        assertThat(outbox.renewLease("lease", sendStart, sendStart.plusSeconds(30), 3)).isTrue();
        assertThat(outbox.getLockedUntil()).isEqualTo(sendStart.plusSeconds(30));
        assertThat(outbox.getDeliveryCount()).isOne();
        assertThat(outbox.delivered("lease", sendStart.plusSeconds(1))).isTrue();
    }

    @Test
    void sensitiveValueObjectsUseRedactedRepresentations() {
        ClaimedEmail claimed = new ClaimedEmail(java.util.UUID.randomUUID(), "secret-lease",
                EmailTemplateType.NEW_INQUIRY, "recipient@example.invalid", 1);
        RenderedEmail rendered = new RenderedEmail("Sensitive subject", "Sensitive body");

        assertThat(claimed.toString()).isEqualTo("ClaimedEmail[redacted]")
                .doesNotContain("recipient", "secret-lease", claimed.outboxId().toString());
        assertThat(rendered.toString()).isEqualTo("RenderedEmail[redacted]")
                .doesNotContain("Sensitive subject", "Sensitive body");
    }

    @Test
    void exhaustedRowsAreTerminal() {
        Instant now = Instant.parse("2026-08-24T10:00:00Z");
        EmailOutbox outbox = EmailOutbox.create(EmailTemplateType.PASSWORD_CHANGED,
                "recipient@example.invalid", null, "password:one", now);
        outbox.claim("lease", now, now.plusSeconds(30));
        assertThat(outbox.exhaust("lease", EmailFailureCode.REJECTED)).isTrue();
        assertThat(outbox.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(outbox.claim("again", now.plusSeconds(60), now.plusSeconds(90))).isFalse();
    }
}
