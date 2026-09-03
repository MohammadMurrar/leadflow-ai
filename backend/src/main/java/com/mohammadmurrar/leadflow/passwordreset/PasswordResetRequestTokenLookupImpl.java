package com.mohammadmurrar.leadflow.passwordreset;

import jakarta.persistence.EntityManager;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public class PasswordResetRequestTokenLookupImpl implements PasswordResetRequestTokenLookup {
    private static final String QUERY = "select request from PasswordResetRequest request "
            + "where request.tokenHash = :tokenHash";
    private final EntityManager entityManager;

    public PasswordResetRequestTokenLookupImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PasswordResetRequest> findByTokenHash(byte[] tokenHash) {
        if (tokenHash == null || tokenHash.length != 32) {
            throw new IllegalArgumentException("Password reset token hash is invalid");
        }
        byte[] queryHash = tokenHash.clone();
        List<PasswordResetRequest> results = entityManager
                .createQuery(QUERY, PasswordResetRequest.class)
                .setParameter("tokenHash", queryHash)
                .getResultList();
        return results.stream().findFirst();
    }
}
