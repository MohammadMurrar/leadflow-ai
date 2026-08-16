CREATE TABLE workspace_settings (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    singleton_key TINYINT NOT NULL DEFAULT 1,
    workspace_name VARCHAR(120) NOT NULL,
    contact_email VARCHAR(180),
    description VARCHAR(500),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_workspace_settings_singleton UNIQUE (singleton_key),
    CONSTRAINT chk_workspace_settings_singleton CHECK (singleton_key = 1)
);

INSERT INTO workspace_settings (
    id, version, singleton_key, workspace_name, contact_email, description, created_at, updated_at
) VALUES (
    UUID_TO_BIN(UUID()), 0, 1, 'My Workspace', NULL, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
);
