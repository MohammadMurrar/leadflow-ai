package com.mohammadmurrar.leadflow.qualification.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QualificationStartRequest(
        @NotBlank @Size(max = 100) String workflowExecutionId) {
}
