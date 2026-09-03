package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.User;

import java.util.Locale;

public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static void validateNewPassword(String password, String email) {
        if (password == null || password.length() < 12 || password.length() > 128
                || !password.equals(password.trim())
                || password.chars().anyMatch(Character::isISOControl)
                || password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit)) {
            throw new InvalidPasswordException(Reason.STRUCTURE);
        }
        String normalizedEmail = User.normalizeEmail(email);
        String localPart = normalizedEmail.substring(0, normalizedEmail.indexOf('@'));
        String lowered = password.toLowerCase(Locale.ROOT);
        if (lowered.contains(normalizedEmail) || (!localPart.isBlank() && lowered.contains(localPart))) {
            throw new InvalidPasswordException(Reason.EMAIL);
        }
    }

    public static final class InvalidPasswordException extends RuntimeException {
        private final Reason reason;
        public InvalidPasswordException(Reason reason) {
            super("Password does not meet the security requirements");
            this.reason = reason;
        }
        public Reason reason() { return reason; }
    }

    public enum Reason { STRUCTURE, EMAIL }
}
