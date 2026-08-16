package com.mohammadmurrar.leadflow.settings.api;

public record AutomationStatusResponse(
        boolean dispatcherEnabled,
        boolean legacyCallbackEnabled,
        boolean retryEnabled,
        boolean attemptTrackingAvailable) {
}
