package com.mohammadmurrar.leadflow.email;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
class EmailOutboxInsertDao {
    private static final String INSERT = """
            INSERT INTO email_outbox
                (id, version, template_type, recipient, lead_id, password_reset_request_id, workspace_id, deduplication_key,
                 status, delivery_count, available_at, created_at, updated_at)
            VALUES (?, 0, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    EmailOutboxInsertDao(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean insertIfAbsent(UUID id, String templateType, String recipient, UUID leadId, UUID workspaceId,
            String deduplicationKey, Instant now) {
        return insertIfAbsent(id, templateType, recipient, leadId, null, workspaceId, deduplicationKey, now);
    }

    boolean insertIfAbsent(UUID id, String templateType, String recipient, UUID leadId,
            UUID passwordResetRequestId, UUID workspaceId, String deduplicationKey, Instant now) {
        Timestamp timestamp = Timestamp.from(now);
        try {
            jdbcTemplate.update(INSERT, uuidBytes(id), templateType, recipient,
                    leadId == null ? null : uuidBytes(leadId),
                    passwordResetRequestId == null ? null : uuidBytes(passwordResetRequestId),
                    uuidBytes(java.util.Objects.requireNonNull(workspaceId, "Workspace is required")),
                    deduplicationKey, timestamp, timestamp, timestamp);
            return true;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    private byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
