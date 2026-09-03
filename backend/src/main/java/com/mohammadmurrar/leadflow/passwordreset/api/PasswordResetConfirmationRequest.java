package com.mohammadmurrar.leadflow.passwordreset.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmationRequest(
        @NotNull(message = "Password reset link is invalid")
        @Size(min = 43, max = 43, message = "Password reset link is invalid")
        @Pattern(regexp = "^[A-Za-z0-9_-]{43}$", message = "Password reset link is invalid")
        @JsonDeserialize(using = StrictStringDeserializer.class) String token,
        @NotNull(message = "Enter a new password")
        @Size(min = 12, max = 128,
                message = "Use 12 to 128 characters with uppercase, lowercase, number, and symbol")
        @JsonDeserialize(using = StrictStringDeserializer.class) String newPassword
) {
    @JsonAnySetter
    public void rejectUnknownProperty(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown password reset property");
    }

    @Override public String toString() { return "PasswordResetConfirmationRequest[redacted]"; }
}
