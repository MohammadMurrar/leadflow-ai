package com.mohammadmurrar.leadflow.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {
    Optional<WorkspaceSettings> findBySingletonKey(byte singletonKey);
    Optional<WorkspaceSettings> findByWorkspaceId(UUID workspaceId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO workspace_settings
                (id, version, singleton_key, workspace_id, workspace_name, contact_email, description,
                 time_zone, currency, response_time_text, created_at, updated_at)
            VALUES (:id, 0, 1, :workspaceId, 'My Workspace', NULL, NULL, 'UTC', 'USD',
                    'We usually respond within one business day.', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, nativeQuery = true)
    int initializeIfMissing(UUID id, UUID workspaceId);
}
