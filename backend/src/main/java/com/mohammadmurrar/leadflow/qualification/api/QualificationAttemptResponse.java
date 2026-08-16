package com.mohammadmurrar.leadflow.qualification.api;

import com.mohammadmurrar.leadflow.qualification.*;
import java.time.Instant;
import java.util.UUID;

public record QualificationAttemptResponse(
        UUID id,
        int attemptNumber,
        QualificationAttemptStatus status,
        QualificationFailureCode failureCode,
        String failureMessage,
        Instant dispatchedAt,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        Instant timedOutAt,
        Instant createdAt,
        Instant updatedAt) {
    public static QualificationAttemptResponse from(QualificationAttempt attempt) {
        return new QualificationAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStatus(),
                attempt.getFailureCode(), attempt.getFailureMessage(), attempt.getDispatchedAt(), attempt.getStartedAt(),
                attempt.getCompletedAt(), attempt.getFailedAt(), attempt.getTimedOutAt(), attempt.getCreatedAt(),
                attempt.getUpdatedAt());
    }
}
