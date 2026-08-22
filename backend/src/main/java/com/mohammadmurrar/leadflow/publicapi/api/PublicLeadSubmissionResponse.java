package com.mohammadmurrar.leadflow.publicapi.api;

public record PublicLeadSubmissionResponse(String message) {
    private static final PublicLeadSubmissionResponse RECEIVED =
            new PublicLeadSubmissionResponse("Thank you. Your inquiry has been received.");

    public static PublicLeadSubmissionResponse received() {
        return RECEIVED;
    }
}
