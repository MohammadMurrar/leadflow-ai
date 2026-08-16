package com.mohammadmurrar.leadflow.service.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateServiceRequest(
        @NotBlank @Size(max = 500) String name,
        @Size(max = 1000) String description) {}
