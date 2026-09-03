package com.mohammadmurrar.leadflow.email;

public interface EmailSender {
    void send(String recipient, RenderedEmail email);
}
