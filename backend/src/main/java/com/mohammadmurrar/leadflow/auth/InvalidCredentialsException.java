package com.mohammadmurrar.leadflow.auth;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Invalid email or password"); }
}
