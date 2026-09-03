package com.mohammadmurrar.leadflow.email;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Component
public class EmailIntentKeyFactory {
    public String key(String namespace, UUID eventId, String recipient) {
        String normalizedRecipient = recipient.trim().toLowerCase(Locale.ROOT);
        return namespace + ":" + eventId + "-" + sha256(normalizedRecipient);
    }

    public String concealedKey(String namespace, UUID eventId, String recipient) {
        String normalizedRecipient = recipient.trim().toLowerCase(Locale.ROOT);
        return namespace + ":" + sha256(eventId + ":" + normalizedRecipient);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
