package com.mohammadmurrar.leadflow.qualification;

public enum QualificationFailureCode {
    WEBHOOK_DELIVERY_FAILED,
    WORKFLOW_FAILED,
    AI_PROVIDER_ERROR,
    INVALID_AI_RESPONSE,
    CALLBACK_REJECTED,
    TIMEOUT,
    UNKNOWN
}
