package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.lead.LeadPriority;
import jakarta.validation.constraints.*;

public record QualificationSuccessRequest(
        @Min(0) @Max(100) int score,
        @NotNull LeadPriority priority,
        @NotBlank @Size(max = 120) String category,
        @NotBlank @Size(max = 1200) String summary,
        @NotBlank @Size(max = 1800) String recommendedReply,
        @NotBlank @Size(max = 100) String workflowExecutionId) {
}
