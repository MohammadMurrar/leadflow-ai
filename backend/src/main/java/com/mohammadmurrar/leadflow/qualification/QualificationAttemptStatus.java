package com.mohammadmurrar.leadflow.qualification;

public enum QualificationAttemptStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == TIMED_OUT;
    }
}
