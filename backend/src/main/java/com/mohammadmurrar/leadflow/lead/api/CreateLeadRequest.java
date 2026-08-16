package com.mohammadmurrar.leadflow.lead.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLeadRequest(
        @NotBlank @Size(max = 100) String fullName,
        @NotBlank @Email @Size(max = 180) String email,
        @Size(max = 30) String phone,
        @Size(max = 140) String company,
        UUID serviceId,
        @Size(max = 120) String requestedService,
        @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal estimatedBudget,
        @FutureOrPresent LocalDate desiredStartDate,
        @NotBlank @Size(min = 20, max = 3000) String message,
        @Size(max = 60) String source
) {
    public CreateLeadRequest(String fullName, String email, String phone, String company,
            String requestedService, BigDecimal estimatedBudget, LocalDate desiredStartDate,
            String message, String source) {
        this(fullName, email, phone, company, null, requestedService, estimatedBudget,
                desiredStartDate, message, source);
    }
}
