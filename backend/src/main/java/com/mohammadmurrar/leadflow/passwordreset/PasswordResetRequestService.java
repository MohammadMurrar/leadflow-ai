package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.user.*;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PasswordResetRequestService {
    private final UserRepository users;
    private final PasswordResetRequestRepository requests;
    private final PasswordResetTokenService tokens;
    private final PasswordResetProperties properties;
    private final EmailOutboxService outbox;
    private final EmailIntentKeyFactory keys;

    @Autowired
    public PasswordResetRequestService(UserRepository users, PasswordResetRequestRepository requests,
            PasswordResetTokenService tokens, PasswordResetProperties properties,
            EmailOutboxService outbox, EmailIntentKeyFactory keys) {
        this.users = users;
        this.requests = requests;
        this.tokens = tokens;
        this.properties = properties;
        this.outbox = outbox;
        this.keys = keys;
    }

    @Transactional
    public Result request(String submittedEmail, Instant requestedAt) {
        String email = normalize(submittedEmail);
        if (email == null || !properties.enabled()) return Result.SUPPRESSED;
        Optional<User> candidate = users.findByNormalizedEmail(email);
        if (candidate.isEmpty()) return Result.SUPPRESSED;
        User user = users.findByIdForUpdate(candidate.get().getId()).orElse(null);
        if (user == null || !user.isEnabled() || user.getRole() != UserRole.ADMIN
                || !email.equals(user.getNormalizedEmail())) return Result.SUPPRESSED;

        Instant now = Objects.requireNonNull(requestedAt).truncatedTo(ChronoUnit.MICROS);
        Optional<PasswordResetRequest> active = requests.findActiveByUserIdForUpdate(user.getId());
        if (active.isPresent() && now.isBefore(properties.cooldownEndsAt(active.get().getCreatedAt()))) {
            return Result.SUPPRESSED;
        }
        if (active.isPresent()) {
            active.get().supersede(now);
            requests.flush();
        }

        UUID requestId = UUID.randomUUID();
        Instant expiresAt = properties.tokenExpiresAt(now);
        byte[] nonce = tokens.newDeliveryNonce();
        byte[] tokenBytes = null;
        byte[] tokenHash = null;
        try (PasswordResetTokenService.SensitiveToken token = tokens.derive(requestId, user.getId(),
                expiresAt, nonce, tokens.activeKeyVersion())) {
            tokenBytes = token.bytes();
            tokenHash = tokens.storedHash(tokenBytes);
            PasswordResetRequest request = PasswordResetRequest.createActive(requestId, user,
                    tokenHash, nonce, tokens.activeKeyVersion(), now, expiresAt);
            requests.saveAndFlush(request);
            outbox.enqueuePasswordReset(email, request,
                    keys.concealedKey("password-reset", requestId, email), now);
        } finally {
            if (nonce != null) Arrays.fill(nonce, (byte) 0);
            if (tokenBytes != null) Arrays.fill(tokenBytes, (byte) 0);
            if (tokenHash != null) Arrays.fill(tokenHash, (byte) 0);
        }
        return Result.CREATED;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > 254) return null;
        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
            return address.getAddress().equals(normalized) ? normalized : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public enum Result {
        CREATED, SUPPRESSED;
        @Override public String toString() { return "PasswordResetRequestResult[redacted]"; }
    }
}
