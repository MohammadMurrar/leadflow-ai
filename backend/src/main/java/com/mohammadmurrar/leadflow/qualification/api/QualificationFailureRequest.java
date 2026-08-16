package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.qualification.QualificationFailureCode;
import jakarta.validation.constraints.*;

public record QualificationFailureRequest(
        @NotNull QualificationFailureCode failureCode,
        @NotBlank @Size(max = 300) String message,
        @NotBlank @Size(max = 100) String workflowExecutionId) {
}
