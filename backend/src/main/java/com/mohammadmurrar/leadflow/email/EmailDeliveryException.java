package com.mohammadmurrar.leadflow.email;

public class EmailDeliveryException extends RuntimeException {
    private final EmailFailureCode failureCode;

    public EmailDeliveryException(EmailFailureCode failureCode) {
        super("Email provider did not accept the message");
        this.failureCode = failureCode;
    }

    public EmailFailureCode failureCode() { return failureCode; }
}
