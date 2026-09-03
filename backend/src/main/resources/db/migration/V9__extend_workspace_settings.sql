ALTER TABLE workspace_settings
    ADD COLUMN public_brand_name VARCHAR(120) NULL,
    ADD COLUMN public_tagline VARCHAR(240) NULL,
    ADD COLUMN public_logo_path VARCHAR(500) NULL,
    ADD COLUMN time_zone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN response_time_text VARCHAR(240) NOT NULL DEFAULT 'We usually respond within one business day.',
    ADD COLUMN privacy_policy_url VARCHAR(2048) NULL,
    ADD COLUMN privacy_notice_text VARCHAR(1000) NULL,
    ADD COLUMN privacy_notice_version VARCHAR(64) NULL;

CREATE TABLE workspace_notification_recipients (
    id BINARY(16) NOT NULL,
    workspace_settings_id BINARY(16) NOT NULL,
    normalized_email VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_workspace_notification_recipient_settings
        FOREIGN KEY (workspace_settings_id) REFERENCES workspace_settings(id) ON DELETE CASCADE,
    CONSTRAINT uk_workspace_notification_recipient_email
        UNIQUE (workspace_settings_id, normalized_email)
);
