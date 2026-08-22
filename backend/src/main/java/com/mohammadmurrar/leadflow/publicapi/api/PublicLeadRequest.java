package com.mohammadmurrar.leadflow.publicapi.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PublicLeadRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 30) String phone,
        @Size(max = 140) String company,
        @NotNull UUID serviceId,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal estimatedBudget,
        @FutureOrPresent LocalDate desiredStartDate,
        @NotBlank @Size(min = 20, max = 3000) String message,
        @Size(max = 200) String website
) {
    public PublicLeadRequest {
        fullName = trim(fullName);
        email = trim(email);
        phone = normalizeOptional(phone);
        company = normalizeOptional(company);
        message = trim(message);
        website = trim(website);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptional(String value) {
        String normalized = trim(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String ignoredName, Object ignoredValue) {
        throw new IllegalArgumentException("Unknown public inquiry property");
    }
}
