package com.mohammadmurrar.leadflow.passwordreset;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public final class PasswordResetTokenService {
    private static final byte[] DOMAIN = "leadflow-password-reset-v1".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern TOKEN_TEXT = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private final PasswordResetKeyRing keys;
    private final SecureRandom random;

    @Autowired
    public PasswordResetTokenService(PasswordResetProperties properties) {
        this(properties.validatedKeyRing(), new SecureRandom());
    }

    PasswordResetTokenService(PasswordResetKeyRing keys, SecureRandom random) {
        this.keys = Objects.requireNonNull(keys);
        this.random = Objects.requireNonNull(random);
    }

    public byte[] newDeliveryNonce() {
        byte[] nonce = new byte[32];
        random.nextBytes(nonce);
        return nonce;
    }

    public SensitiveToken derive(UUID requestId, UUID userId, Instant expiresAt,
            byte[] deliveryNonce, String keyVersion) {
        requireNonce(deliveryNonce);
        byte[] key = keys.resolve(keyVersion);
        byte[] message = canonicalMessage(requestId, userId, expiresAt, deliveryNonce);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return new SensitiveToken(mac.doFinal(message));
        } catch (GeneralSecurityException exception) {
            throw new PasswordResetCryptographyException();
        } finally {
            Arrays.fill(key, (byte) 0);
            Arrays.fill(message, (byte) 0);
        }
    }

    public byte[] storedHash(byte[] tokenBytes) {
        if (tokenBytes == null || tokenBytes.length != 32) throw new InvalidPasswordResetTokenException();
        try {
            return MessageDigest.getInstance("SHA-256").digest(tokenBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new PasswordResetCryptographyException();
        }
    }

    public byte[] decodeSubmitted(String encodedToken) {
        if (encodedToken == null || !TOKEN_TEXT.matcher(encodedToken).matches()) {
            throw new InvalidPasswordResetTokenException();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encodedToken);
            if (decoded.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(decoded).equals(encodedToken)) {
                Arrays.fill(decoded, (byte) 0);
                throw new InvalidPasswordResetTokenException();
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordResetTokenException();
        }
    }

    public boolean matches(byte[] expectedStoredHash, byte[] submittedTokenBytes) {
        if (expectedStoredHash == null || expectedStoredHash.length != 32
                || submittedTokenBytes == null || submittedTokenBytes.length != 32) return false;
        byte[] submittedHash = storedHash(submittedTokenBytes);
        try {
            return MessageDigest.isEqual(expectedStoredHash, submittedHash);
        } finally {
            Arrays.fill(submittedHash, (byte) 0);
        }
    }

    public String activeKeyVersion() { return keys.activeVersion(); }

    private static byte[] canonicalMessage(UUID requestId, UUID userId, Instant expiresAt,
            byte[] nonce) {
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(expiresAt);
        // Binary layout, big-endian: 26-byte ASCII domain; request UUID (two longs);
        // user UUID (two longs); expiry epoch-second (long) and nano (int); 32-byte nonce.
        ByteBuffer buffer = ByteBuffer.allocate(DOMAIN.length + 16 + 16 + 12 + 32)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.put(DOMAIN);
        putUuid(buffer, requestId);
        putUuid(buffer, userId);
        buffer.putLong(expiresAt.getEpochSecond()).putInt(expiresAt.getNano());
        buffer.put(nonce);
        return buffer.array();
    }

    private static void putUuid(ByteBuffer buffer, UUID value) {
        buffer.putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits());
    }

    private static void requireNonce(byte[] nonce) {
        if (nonce == null || nonce.length != 32) throw new PasswordResetCryptographyException();
    }

    @Override
    public String toString() { return "PasswordResetTokenService[redacted]"; }

    public static final class SensitiveToken implements AutoCloseable {
        private byte[] bytes;
        private SensitiveToken(byte[] bytes) { this.bytes = bytes; }
        public byte[] bytes() {
            if (bytes == null) throw new IllegalStateException("Password reset token is cleared");
            return bytes.clone();
        }
        public String encoded() {
            if (bytes == null) throw new IllegalStateException("Password reset token is cleared");
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        }
        public void clear() {
            if (bytes != null) Arrays.fill(bytes, (byte) 0);
            bytes = null;
        }
        @Override public void close() { clear(); }
        @Override public String toString() { return "SensitiveToken[redacted]"; }
    }

    public static final class InvalidPasswordResetTokenException extends RuntimeException {
        public InvalidPasswordResetTokenException() { super("Password reset token is invalid"); }
    }

    public static final class PasswordResetCryptographyException extends RuntimeException {
        public PasswordResetCryptographyException() { super("Password reset cryptography is unavailable"); }
    }
}
