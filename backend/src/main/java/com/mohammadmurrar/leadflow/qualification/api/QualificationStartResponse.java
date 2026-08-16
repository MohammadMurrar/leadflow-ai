package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.qualification.QualificationAttemptStatus;
import java.util.UUID;

public record QualificationStartResponse(UUID attemptId, QualificationAttemptStatus status, boolean accepted) {
}
