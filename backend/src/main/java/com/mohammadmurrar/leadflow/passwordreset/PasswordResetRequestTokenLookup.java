package com.mohammadmurrar.leadflow.passwordreset;

import java.util.Optional;

public interface PasswordResetRequestTokenLookup {
    Optional<PasswordResetRequest> findByTokenHash(byte[] tokenHash);
}
