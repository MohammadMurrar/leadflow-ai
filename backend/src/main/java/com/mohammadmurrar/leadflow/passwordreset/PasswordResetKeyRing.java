package com.mohammadmurrar.leadflow.passwordreset;

import java.util.*;

public final class PasswordResetKeyRing {
    private final boolean enabled;
    private final String activeVersion;
    private final Map<String, byte[]> keys;

    private PasswordResetKeyRing(boolean enabled, String activeVersion, Map<String, byte[]> keys) {
        this.enabled = enabled;
        this.activeVersion = activeVersion;
        Map<String, byte[]> copies = new HashMap<>();
        keys.forEach((version, key) -> copies.put(version, key.clone()));
        this.keys = Collections.unmodifiableMap(copies);
    }

    static PasswordResetKeyRing disabled() {
        return new PasswordResetKeyRing(false, null, Map.of());
    }

    static PasswordResetKeyRing enabled(String activeVersion, Map<String, byte[]> keys) {
        return new PasswordResetKeyRing(true, activeVersion, keys);
    }

    public boolean enabled() { return enabled; }
    public String activeVersion() {
        if (!enabled) throw new PasswordResetKeyUnavailableException();
        return activeVersion;
    }
    byte[] resolve(String version) {
        byte[] key = keys.get(version);
        if (!enabled || key == null) throw new PasswordResetKeyUnavailableException();
        return key.clone();
    }

    @Override
    public String toString() { return "PasswordResetKeyRing[redacted]"; }

    public static final class PasswordResetKeyUnavailableException extends RuntimeException {
        public PasswordResetKeyUnavailableException() {
            super("Password reset key is unavailable");
        }
    }
}
