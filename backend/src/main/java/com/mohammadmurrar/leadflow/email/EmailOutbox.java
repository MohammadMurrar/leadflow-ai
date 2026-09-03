package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "email_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uk_email_outbox_deduplication", columnNames = "deduplication_key"))
public class EmailOutbox {
    @Id private UUID id;
    @Version private long version;
    @Enumerated(EnumType.STRING) @Column(name = "template_type", nullable = false, length = 40)
    private EmailTemplateType templateType;
    @Column(nullable = false, length = 254) private String recipient;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Lead lead;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "password_reset_request_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private PasswordResetRequest passwordResetRequest;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;
    @Column(name = "deduplication_key", nullable = false, length = 180)
    private String deduplicationKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EmailOutboxStatus status;
    @Column(name = "delivery_count", nullable = false) private int deliveryCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "lease_token", length = 36) private String leaseToken;
    @Column(name = "locked_until") private Instant lockedUntil;
    @Column(name = "delivered_at") private Instant deliveredAt;
    @Enumerated(EnumType.STRING) @Column(name = "failure_code", length = 40)
    private EmailFailureCode failureCode;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected EmailOutbox() {}

    static EmailOutbox create(EmailTemplateType templateType, String recipient, Lead lead,
            String deduplicationKey, Instant now) {
        EmailOutbox outbox = new EmailOutbox();
        outbox.id = UUID.randomUUID();
        outbox.templateType = Objects.requireNonNull(templateType);
        outbox.recipient = Objects.requireNonNull(recipient);
        outbox.lead = lead;
        outbox.workspace = lead == null ? null : Objects.requireNonNull(lead.getWorkspace(), "Workspace is required");
        outbox.deduplicationKey = Objects.requireNonNull(deduplicationKey);
        outbox.status = EmailOutboxStatus.PENDING;
        outbox.availableAt = Objects.requireNonNull(now);
        return outbox;
    }

    boolean claim(String token, Instant now, Instant lockedUntil) {
        boolean eligible = status == EmailOutboxStatus.PENDING && !availableAt.isAfter(now);
        boolean expired = status == EmailOutboxStatus.IN_PROGRESS
                && this.lockedUntil != null && !this.lockedUntil.isAfter(now);
        if (!eligible && !expired) return false;
        status = EmailOutboxStatus.IN_PROGRESS;
        deliveryCount++;
        leaseToken = Objects.requireNonNull(token);
        this.lockedUntil = Objects.requireNonNull(lockedUntil);
        return true;
    }

    boolean delivered(String token, Instant now) {
        if (!ownsLease(token)) return false;
        status = EmailOutboxStatus.DELIVERED;
        deliveredAt = now;
        clearLease();
        return true;
    }

    boolean ownsActiveLease(String token, Instant now) {
        return ownsLease(token) && lockedUntil != null && lockedUntil.isAfter(now);
    }

    boolean renewLease(String token, Instant now, Instant newLockedUntil, int maximumAttempts) {
        if (!ownsActiveLease(token, now) || deliveryCount > maximumAttempts) return false;
        lockedUntil = Objects.requireNonNull(newLockedUntil);
        return true;
    }

    boolean exhaustWithoutClaimIfAttemptsReached(int maximumAttempts, Instant now) {
        boolean eligible = status == EmailOutboxStatus.PENDING && !availableAt.isAfter(now);
        boolean expired = status == EmailOutboxStatus.IN_PROGRESS
                && lockedUntil != null && !lockedUntil.isAfter(now);
        if ((!eligible && !expired) || deliveryCount < maximumAttempts) return false;
        status = EmailOutboxStatus.FAILED;
        if (failureCode == null) failureCode = EmailFailureCode.UNEXPECTED;
        clearLease();
        return true;
    }

    boolean reschedule(String token, EmailFailureCode code, Instant availableAt) {
        if (!ownsLease(token)) return false;
        status = EmailOutboxStatus.PENDING;
        failureCode = Objects.requireNonNull(code);
        this.availableAt = Objects.requireNonNull(availableAt);
        clearLease();
        return true;
    }

    boolean exhaust(String token, EmailFailureCode code) {
        if (!ownsLease(token)) return false;
        status = EmailOutboxStatus.FAILED;
        failureCode = Objects.requireNonNull(code);
        clearLease();
        return true;
    }

    private boolean ownsLease(String token) {
        return status == EmailOutboxStatus.IN_PROGRESS && Objects.equals(leaseToken, token);
    }
    private void clearLease() { leaseToken = null; lockedUntil = null; }
    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public EmailTemplateType getTemplateType() { return templateType; }
    public String getRecipient() { return recipient; }
    public Lead getLead() { return lead; }
    public PasswordResetRequest getPasswordResetRequest() { return passwordResetRequest; }
    public Workspace getWorkspace() { return workspace; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public EmailOutboxStatus getStatus() { return status; }
    public int getDeliveryCount() { return deliveryCount; }
    public Instant getAvailableAt() { return availableAt; }
    public String getLeaseToken() { return leaseToken; }
    public Instant getLockedUntil() { return lockedUntil; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public EmailFailureCode getFailureCode() { return failureCode; }
}
