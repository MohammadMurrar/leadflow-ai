-- singleton_key is retained as deprecated transitional data for compatibility with
-- pre-workspace code. workspace_id is the sole tenant identity after this migration.
CREATE TEMPORARY TABLE v13_workspace_settings_guard (
    valid TINYINT NOT NULL,
    CONSTRAINT chk_v13_workspace_settings_guard CHECK (valid = 1)
);

INSERT INTO v13_workspace_settings_guard (valid)
SELECT IF(
    (SELECT COUNT(*) FROM workspace_settings WHERE workspace_id IS NULL) = 0
    AND (SELECT COUNT(*) FROM workspace_settings settings
         LEFT JOIN workspaces workspace ON workspace.id = settings.workspace_id
         WHERE workspace.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM (
             SELECT workspace_id
             FROM workspace_settings
             GROUP BY workspace_id
             HAVING COUNT(*) > 1
         ) duplicate_settings) = 0
    AND (SELECT COUNT(*) FROM workspace_notification_recipients recipient
         LEFT JOIN workspace_settings settings ON settings.id = recipient.workspace_settings_id
         WHERE settings.id IS NULL OR recipient.workspace_id <> settings.workspace_id) = 0,
    1,
    NULL
);

ALTER TABLE workspace_settings
    DROP INDEX uk_workspace_settings_singleton,
    DROP CHECK chk_workspace_settings_singleton,
    MODIFY singleton_key TINYINT NOT NULL DEFAULT 1
        COMMENT 'Deprecated transitional key; workspace_id is authoritative',
    MODIFY workspace_id BINARY(16) NOT NULL,
    ADD CONSTRAINT uk_workspace_settings_workspace UNIQUE (workspace_id);

DROP TEMPORARY TABLE v13_workspace_settings_guard;
