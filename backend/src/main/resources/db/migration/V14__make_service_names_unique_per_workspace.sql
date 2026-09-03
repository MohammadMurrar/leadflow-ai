-- Validate all ownership and uniqueness preconditions before MySQL's implicit-commit DDL.
CREATE TEMPORARY TABLE v14_service_name_guard (
    valid TINYINT NOT NULL,
    CONSTRAINT chk_v14_service_name_guard CHECK (valid = 1)
);

INSERT INTO v14_service_name_guard (valid)
SELECT IF(
    (SELECT COUNT(*) FROM services WHERE workspace_id IS NULL) = 0
    AND (SELECT COUNT(*) FROM services service
         LEFT JOIN workspaces workspace ON workspace.id = service.workspace_id
         WHERE workspace.id IS NULL) = 0
    AND (SELECT COUNT(*) FROM (
             SELECT workspace_id, normalized_name
             FROM services
             GROUP BY workspace_id, normalized_name
             HAVING COUNT(*) > 1
         ) duplicate_services) = 0,
    1,
    NULL
);

ALTER TABLE services
    DROP INDEX uk_services_normalized_name,
    MODIFY workspace_id BINARY(16) NOT NULL,
    ADD CONSTRAINT uk_services_workspace_normalized_name
        UNIQUE (workspace_id, normalized_name);

DROP TEMPORARY TABLE v14_service_name_guard;
