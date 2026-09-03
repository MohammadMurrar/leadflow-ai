package com.mohammadmurrar.leadflow.passwordreset.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;

public record PasswordResetRequest(
        @NotNull(message = "Enter a valid email address")
        @JsonDeserialize(using = StrictStringDeserializer.class) String email
) {
    @JsonAnySetter
    public void rejectUnknownProperty(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown password reset property");
    }

    @Override public String toString() { return "PasswordResetRequest[redacted]"; }
}
