package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.*;

import java.time.*;
import java.util.*;

@Service
public class PasswordResetEmailDeliveryService {
    private final PasswordResetRequestRepository requests;
    private final PasswordResetTokenService tokens;
    private final EmailTemplateRenderer renderer;

    @Autowired
    public PasswordResetEmailDeliveryService(PasswordResetRequestRepository requests,
            PasswordResetTokenService tokens, EmailTemplateRenderer renderer) {
        this.requests = requests;
        this.tokens = tokens;
        this.renderer = renderer;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public RenderedEmail prepare(UUID requestId, UUID workspaceId, Instant requestedAt) {
        PasswordResetRequest request = requests.findDeliverableByIdAndWorkspaceId(requestId, workspaceId)
                .orElseThrow(PasswordResetEmailUnavailableException::new);
        Instant now = Objects.requireNonNull(requestedAt);
        byte[] nonce = request.getDeliveryNonce();
        byte[] expectedHash = request.getTokenHash();
        if (!request.isActiveAt(now) || request.getEmailDeliveredAt() != null
                || nonce == null || request.getDeliveryKeyVersion() == null) {
            clear(nonce, expectedHash);
            throw new PasswordResetEmailUnavailableException();
        }
        byte[] tokenBytes = null;
        try (PasswordResetTokenService.SensitiveToken token = tokens.derive(request.getId(),
                request.getUser().getId(), request.getExpiresAt(), nonce,
                request.getDeliveryKeyVersion())) {
            tokenBytes = token.bytes();
            if (!tokens.matches(expectedHash, tokenBytes)) {
                throw new PasswordResetEmailUnavailableException();
            }
            return renderer.renderPasswordReset(token.encoded());
        } finally {
            clear(nonce, expectedHash, tokenBytes);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(UUID requestId, UUID workspaceId, Instant deliveredAt) {
        return requests.findDeliverableByIdAndWorkspaceIdForUpdate(requestId, workspaceId)
                .map(request -> request.markEmailDelivered(deliveredAt))
                .orElse(false);
    }

    private static void clear(byte[]... values) {
        for (byte[] value : values) if (value != null) Arrays.fill(value, (byte) 0);
    }

    public static final class PasswordResetEmailUnavailableException extends RuntimeException {
        public PasswordResetEmailUnavailableException() {
            super("Password reset email is unavailable");
        }
    }
}
