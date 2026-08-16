package com.mohammadmurrar.leadflow.settings;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceSettingsRepository extends JpaRepository<WorkspaceSettings, UUID> {
    Optional<WorkspaceSettings> findBySingletonKey(byte singletonKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO workspace_settings
                (id, version, singleton_key, workspace_name, contact_email, description, created_at, updated_at)
            VALUES (:id, 0, 1, 'My Workspace', NULL, NULL, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
            """, nativeQuery = true)
    int initializeIfMissing(UUID id);
}
