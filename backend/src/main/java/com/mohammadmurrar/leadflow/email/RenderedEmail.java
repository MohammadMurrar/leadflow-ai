package com.mohammadmurrar.leadflow.email;

public record RenderedEmail(String subject, String textBody) {

    @Override
    public String toString() {
        return "RenderedEmail[redacted]";
    }
}
