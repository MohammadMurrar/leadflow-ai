package com.mohammadmurrar.leadflow.qualification;

import com.mohammadmurrar.leadflow.lead.Lead;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "qualification_attempts")
public class QualificationAttempt {
    @Id private UUID id;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;
    @Column(nullable = false) private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private QualificationAttemptStatus status;
    @Enumerated(EnumType.STRING) @Column(length = 40)
    private QualificationFailureCode failureCode;
    @Column(length = 300) private String failureMessage;
    @Column(length = 100) private String workflowExecutionId;
    @Column(length = 32, columnDefinition = "BINARY(32)") private byte[] outcomeFingerprint;
    private Instant dispatchedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant failedAt;
    private Instant timedOutAt;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected QualificationAttempt() {}

    public static QualificationAttempt create(Lead lead, int attemptNumber) {
        Objects.requireNonNull(lead, "Lead is required");
        if (attemptNumber < 1) throw new IllegalArgumentException("Attempt number must be positive");
        QualificationAttempt attempt = new QualificationAttempt();
        attempt.id = UUID.randomUUID();
        attempt.lead = lead;
        attempt.workspace = Objects.requireNonNull(lead.getWorkspace(), "Workspace is required");
        attempt.attemptNumber = attemptNumber;
        attempt.status = QualificationAttemptStatus.PENDING;
        return attempt;
    }

    public boolean start(String executionId, Instant now) {
        if (status == QualificationAttemptStatus.PROCESSING) return false;
        requireStatus(QualificationAttemptStatus.PENDING);
        workflowExecutionId = normalize(executionId);
        dispatchedAt = now;
        startedAt = now;
        status = QualificationAttemptStatus.PROCESSING;
        return true;
    }

    public void succeed(byte[] fingerprint, Instant now) {
        requireActive();
        outcomeFingerprint = fingerprint.clone();
        completedAt = now;
        status = QualificationAttemptStatus.SUCCEEDED;
    }

    public void fail(QualificationFailureCode code, String message, byte[] fingerprint, Instant now) {
        requireActive();
        failureCode = Objects.requireNonNull(code);
        failureMessage = normalize(message);
        outcomeFingerprint = fingerprint == null ? null : fingerprint.clone();
        failedAt = now;
        status = QualificationAttemptStatus.FAILED;
    }

    public void timeOut(Instant now) {
        requireActive();
        failureCode = QualificationFailureCode.TIMEOUT;
        failureMessage = "Automated qualification did not complete.";
        timedOutAt = now;
        status = QualificationAttemptStatus.TIMED_OUT;
    }

    public boolean matchesTerminal(QualificationAttemptStatus expected, byte[] fingerprint) {
        return status == expected && Arrays.equals(outcomeFingerprint, fingerprint);
    }

    private void requireActive() {
        if (status != QualificationAttemptStatus.PENDING && status != QualificationAttemptStatus.PROCESSING) {
            throw new IllegalStateException("Qualification attempt is already terminal");
        }
    }

    private void requireStatus(QualificationAttemptStatus expected) {
        if (status != expected) throw new IllegalStateException("Qualification attempt is not " + expected);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public Lead getLead() { return lead; }
    public Workspace getWorkspace() { return workspace; }
    public int getAttemptNumber() { return attemptNumber; }
    public QualificationAttemptStatus getStatus() { return status; }
    public QualificationFailureCode getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getWorkflowExecutionId() { return workflowExecutionId; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getFailedAt() { return failedAt; }
    public Instant getTimedOutAt() { return timedOutAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
