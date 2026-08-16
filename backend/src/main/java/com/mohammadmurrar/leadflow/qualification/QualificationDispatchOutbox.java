package com.mohammadmurrar.leadflow.qualification;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "qualification_dispatch_outbox")
public class QualificationDispatchOutbox {
    @Id private UUID id;
    @Version private long version;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, unique = true)
    private QualificationAttempt attempt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private QualificationDispatchStatus status;
    @Column(nullable = false) private int deliveryCount;
    @Column(nullable = false) private Instant availableAt;
    private Instant lockedAt;
    private Instant lockExpiresAt;
    @Column(length = 100) private String lockedBy;
    private Instant deliveredAt;
    @Enumerated(EnumType.STRING) @Column(length = 40)
    private QualificationFailureCode lastFailureCode;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected QualificationDispatchOutbox() {}

    public static QualificationDispatchOutbox create(QualificationAttempt attempt, Instant now) {
        QualificationDispatchOutbox outbox = new QualificationDispatchOutbox();
        outbox.id = UUID.randomUUID();
        outbox.attempt = Objects.requireNonNull(attempt);
        outbox.status = QualificationDispatchStatus.PENDING;
        outbox.availableAt = now;
        return outbox;
    }

    public void claim(String owner, Instant now, Instant expiresAt) {
        if (status != QualificationDispatchStatus.PENDING &&
                !(status == QualificationDispatchStatus.IN_PROGRESS && lockExpiresAt != null && !lockExpiresAt.isAfter(now))) {
            throw new IllegalStateException("Dispatch is not claimable");
        }
        status = QualificationDispatchStatus.IN_PROGRESS;
        deliveryCount++;
        lockedBy = owner;
        lockedAt = now;
        lockExpiresAt = expiresAt;
    }

    public void delivered(String owner, Instant now) {
        requireOwner(owner);
        status = QualificationDispatchStatus.DELIVERED;
        deliveredAt = now;
        clearLease();
    }

    public void reschedule(String owner, QualificationFailureCode code, Instant availableAt) {
        requireOwner(owner);
        status = QualificationDispatchStatus.PENDING;
        lastFailureCode = code;
        this.availableAt = availableAt;
        clearLease();
    }

    public void exhaust(String owner, QualificationFailureCode code) {
        requireOwner(owner);
        status = QualificationDispatchStatus.FAILED;
        lastFailureCode = code;
        clearLease();
    }

    private void requireOwner(String owner) {
        if (status != QualificationDispatchStatus.IN_PROGRESS || !Objects.equals(lockedBy, owner)) {
            throw new IllegalStateException("Dispatch lease is no longer owned");
        }
    }

    private void clearLease() { lockedBy = null; lockedAt = null; lockExpiresAt = null; }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public QualificationAttempt getAttempt() { return attempt; }
    public QualificationDispatchStatus getStatus() { return status; }
    public int getDeliveryCount() { return deliveryCount; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getLockExpiresAt() { return lockExpiresAt; }
}
