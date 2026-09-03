package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "password_reset_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_password_reset_token_hash", columnNames = "token_hash"),
        @UniqueConstraint(name = "uk_password_reset_active_user", columnNames = {"user_id", "active_slot"})
}, indexes = {
        @Index(name = "idx_password_reset_user_created", columnList = "user_id,created_at"),
        @Index(name = "idx_password_reset_expiry", columnList = "expires_at"),
        @Index(name = "idx_password_reset_cleanup",
                columnList = "active_slot,expires_at,consumed_at,superseded_at")
})
public class PasswordResetRequest {
    @Id private UUID id;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;
    @Column(name = "token_hash", nullable = false, length = 32, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;
    @Column(name = "delivery_nonce", length = 32, columnDefinition = "BINARY(32)")
    private byte[] deliveryNonce;
    @Column(name = "delivery_key_version", length = 32)
    private String deliveryKeyVersion;
    @Column(name = "active_slot")
    private Byte activeSlot;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "email_delivered_at")
    private Instant emailDeliveredAt;
    @Column(name = "consumed_at")
    private Instant consumedAt;
    @Column(name = "superseded_at")
    private Instant supersededAt;

    protected PasswordResetRequest() {}

    public static PasswordResetRequest createActive(UUID id, User user, byte[] tokenHash,
            byte[] deliveryNonce, String deliveryKeyVersion, Instant createdAt, Instant expiresAt) {
        requireLength(tokenHash, 32);
        requireLength(deliveryNonce, 32);
        if (deliveryKeyVersion == null || deliveryKeyVersion.isBlank()
                || deliveryKeyVersion.length() > 32) throw invalid();
        Instant created = precision(createdAt);
        Instant expires = precision(expiresAt);
        if (!expires.isAfter(created)) throw invalid();
        PasswordResetRequest request = new PasswordResetRequest();
        request.id = Objects.requireNonNull(id);
        request.user = Objects.requireNonNull(user);
        request.workspace = Objects.requireNonNull(user.getWorkspace(), "Workspace is required");
        request.tokenHash = tokenHash.clone();
        request.deliveryNonce = deliveryNonce.clone();
        request.deliveryKeyVersion = deliveryKeyVersion;
        request.activeSlot = (byte) 1;
        request.createdAt = created;
        request.expiresAt = expires;
        return request;
    }

    public boolean isActiveAt(Instant now) {
        return activeSlot != null && consumedAt == null && supersededAt == null
                && precision(now).isBefore(expiresAt);
    }

    public boolean consume(Instant now) {
        if (consumedAt != null) return false;
        if (supersededAt != null || activeSlot == null || !precision(now).isBefore(expiresAt)) {
            throw invalidTransition();
        }
        consumedAt = precision(now);
        activeSlot = null;
        return true;
    }

    public boolean supersede(Instant now) {
        if (supersededAt != null) return false;
        if (consumedAt != null || activeSlot == null) throw invalidTransition();
        supersededAt = precision(now);
        activeSlot = null;
        clearDeliveryMaterial();
        return true;
    }

    public boolean markEmailDelivered(Instant now) {
        if (emailDeliveredAt != null) return false;
        emailDeliveredAt = precision(now);
        clearDeliveryMaterial();
        return true;
    }

    public boolean clearDeliveryMaterial() {
        if (deliveryNonce == null && deliveryKeyVersion == null) return false;
        if (deliveryNonce != null) Arrays.fill(deliveryNonce, (byte) 0);
        deliveryNonce = null;
        deliveryKeyVersion = null;
        return true;
    }

    public boolean isTerminalBefore(Instant threshold) {
        Instant terminalAt = consumedAt != null ? consumedAt : supersededAt;
        return terminalAt != null && terminalAt.isBefore(precision(threshold));
    }

    private static Instant precision(Instant value) {
        return Objects.requireNonNull(value).truncatedTo(ChronoUnit.MICROS);
    }

    private static void requireLength(byte[] value, int length) {
        if (value == null || value.length != length) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Password reset request is invalid");
    }

    private static IllegalStateException invalidTransition() {
        return new IllegalStateException("Password reset request transition is invalid");
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public User getUser() { return user; }
    public Workspace getWorkspace() { return workspace; }
    public byte[] getTokenHash() { return tokenHash.clone(); }
    public byte[] getDeliveryNonce() { return deliveryNonce == null ? null : deliveryNonce.clone(); }
    public String getDeliveryKeyVersion() { return deliveryKeyVersion; }
    public Byte getActiveSlot() { return activeSlot; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getEmailDeliveredAt() { return emailDeliveredAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getSupersededAt() { return supersededAt; }

    @Override
    public String toString() { return "PasswordResetRequest[redacted]"; }
}
