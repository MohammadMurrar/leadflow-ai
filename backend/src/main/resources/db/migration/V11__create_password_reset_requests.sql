CREATE TABLE password_reset_requests (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    user_id BINARY(16) NOT NULL,
    token_hash BINARY(32) NOT NULL,
    delivery_nonce BINARY(32) NULL,
    delivery_key_version VARCHAR(32) NULL,
    active_slot TINYINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    email_delivered_at TIMESTAMP(6) NULL,
    consumed_at TIMESTAMP(6) NULL,
    superseded_at TIMESTAMP(6) NULL,
    CONSTRAINT pk_password_reset_requests PRIMARY KEY (id),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_password_reset_token_hash UNIQUE (token_hash),
    CONSTRAINT uk_password_reset_active_user UNIQUE (user_id, active_slot),
    CONSTRAINT chk_password_reset_active_slot CHECK (active_slot IS NULL OR active_slot = 1),
    CONSTRAINT chk_password_reset_expiry CHECK (expires_at > created_at),
    CONSTRAINT chk_password_reset_terminal CHECK (consumed_at IS NULL OR superseded_at IS NULL),
    CONSTRAINT chk_password_reset_delivery_material CHECK (
        (delivery_nonce IS NULL AND delivery_key_version IS NULL)
        OR (delivery_nonce IS NOT NULL AND delivery_key_version IS NOT NULL)
    ),
    CONSTRAINT chk_password_reset_active_terminal CHECK (
        active_slot IS NULL OR (consumed_at IS NULL AND superseded_at IS NULL)
    ),
    INDEX idx_password_reset_user_created (user_id, created_at),
    INDEX idx_password_reset_expiry (expires_at),
    INDEX idx_password_reset_cleanup (active_slot, expires_at, consumed_at, superseded_at)
) ENGINE=InnoDB;

ALTER TABLE email_outbox
    ADD COLUMN password_reset_request_id BINARY(16) NULL,
    ADD CONSTRAINT fk_email_outbox_password_reset
        FOREIGN KEY (password_reset_request_id) REFERENCES password_reset_requests(id) ON DELETE SET NULL,
    ADD INDEX idx_email_outbox_password_reset (password_reset_request_id);

-- EmailOutboxService enforces event-specific associations when an intent is created.
-- Both associations remain nullable so ON DELETE SET NULL can preserve delivery history.

ALTER TABLE email_outbox DROP CHECK chk_email_outbox_status;
ALTER TABLE email_outbox ADD CONSTRAINT chk_email_outbox_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DELIVERED', 'FAILED', 'CANCELLED'));

ALTER TABLE email_outbox DROP CHECK chk_email_outbox_template_type;
ALTER TABLE email_outbox ADD CONSTRAINT chk_email_outbox_template_type
    CHECK (template_type IN ('NEW_INQUIRY', 'QUALIFICATION_COMPLETED',
        'QUALIFICATION_NEEDS_ATTENTION', 'PASSWORD_CHANGED', 'PASSWORD_RESET'));
