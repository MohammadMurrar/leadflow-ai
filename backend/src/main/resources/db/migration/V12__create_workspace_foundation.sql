CREATE TABLE workspaces (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    public_slug VARCHAR(63) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT pk_workspaces PRIMARY KEY (id),
    CONSTRAINT uk_workspaces_public_slug UNIQUE (public_slug),
    CONSTRAINT chk_workspaces_status CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED'))
) ENGINE=InnoDB;

-- The guard makes a missing or non-unique legacy settings row fail the migration
-- instead of allowing an ambiguous workspace backfill.
CREATE TEMPORARY TABLE v12_legacy_workspace_guard (
    id BINARY(16) NOT NULL,
    CONSTRAINT pk_v12_legacy_workspace_guard PRIMARY KEY (id)
);

INSERT INTO v12_legacy_workspace_guard (id)
SELECT IF(COUNT(*) = 1, MAX(id), NULL)
FROM workspace_settings
WHERE singleton_key = 1;

INSERT INTO workspaces (
    id, version, public_slug, display_name, status, created_at, updated_at
)
SELECT settings.id, 0, 'leadflow-ai',
       COALESCE(NULLIF(TRIM(settings.workspace_name), ''), 'LeadFlow AI'),
       'ACTIVE', settings.created_at, settings.updated_at
FROM workspace_settings settings
JOIN v12_legacy_workspace_guard legacy ON legacy.id = settings.id
WHERE settings.singleton_key = 1;

ALTER TABLE workspace_settings
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_workspace_settings_workspace (workspace_id),
    ADD CONSTRAINT fk_workspace_settings_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE workspace_notification_recipients
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_workspace_notification_recipients_workspace (workspace_id),
    ADD CONSTRAINT fk_workspace_notification_recipients_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE users
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_users_workspace (workspace_id),
    ADD CONSTRAINT fk_users_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE services
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_services_workspace (workspace_id),
    ADD CONSTRAINT fk_services_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE leads
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_leads_workspace (workspace_id),
    ADD CONSTRAINT fk_leads_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE notifications
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_notifications_workspace (workspace_id),
    ADD CONSTRAINT fk_notifications_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE qualification_attempts
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_qualification_attempts_workspace (workspace_id),
    ADD CONSTRAINT fk_qualification_attempts_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE qualification_dispatch_outbox
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_qualification_dispatch_outbox_workspace (workspace_id),
    ADD CONSTRAINT fk_qualification_dispatch_outbox_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE password_reset_requests
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_password_reset_requests_workspace (workspace_id),
    ADD CONSTRAINT fk_password_reset_requests_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

ALTER TABLE email_outbox
    ADD COLUMN workspace_id BINARY(16) NULL,
    ADD INDEX idx_email_outbox_workspace (workspace_id),
    ADD CONSTRAINT fk_email_outbox_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id) ON DELETE RESTRICT;

UPDATE workspace_settings settings
JOIN v12_legacy_workspace_guard legacy
SET settings.workspace_id = legacy.id
WHERE settings.singleton_key = 1;

UPDATE workspace_notification_recipients recipient
JOIN workspace_settings settings ON settings.id = recipient.workspace_settings_id
SET recipient.workspace_id = settings.workspace_id;

UPDATE users user_account
JOIN v12_legacy_workspace_guard legacy
SET user_account.workspace_id = legacy.id;

UPDATE services service
JOIN v12_legacy_workspace_guard legacy
SET service.workspace_id = legacy.id;

UPDATE leads lead_record
JOIN v12_legacy_workspace_guard legacy
SET lead_record.workspace_id = legacy.id;

UPDATE notifications notification
LEFT JOIN leads lead_record ON lead_record.id = notification.lead_id
JOIN v12_legacy_workspace_guard legacy
SET notification.workspace_id = COALESCE(lead_record.workspace_id, legacy.id);

UPDATE qualification_attempts attempt
JOIN leads lead_record ON lead_record.id = attempt.lead_id
SET attempt.workspace_id = lead_record.workspace_id;

UPDATE qualification_dispatch_outbox dispatch
JOIN qualification_attempts attempt ON attempt.id = dispatch.attempt_id
SET dispatch.workspace_id = attempt.workspace_id;

UPDATE password_reset_requests reset_request
JOIN users user_account ON user_account.id = reset_request.user_id
SET reset_request.workspace_id = user_account.workspace_id;

UPDATE email_outbox outbox
LEFT JOIN leads lead_record ON lead_record.id = outbox.lead_id
LEFT JOIN password_reset_requests reset_request
    ON reset_request.id = outbox.password_reset_request_id
JOIN v12_legacy_workspace_guard legacy
SET outbox.workspace_id = COALESCE(
    lead_record.workspace_id,
    reset_request.workspace_id,
    legacy.id
);

DROP TEMPORARY TABLE v12_legacy_workspace_guard;
