package com.mohammadmurrar.leadflow.workspace;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    boolean existsByPublicSlug(String publicSlug);
    Optional<Workspace> findByPublicSlug(String publicSlug);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select workspace from Workspace workspace where workspace.publicSlug = :publicSlug")
    Optional<Workspace> findByPublicSlugForUpdate(String publicSlug);
    Optional<Workspace> findByIdAndStatus(UUID id, WorkspaceStatus status);
    Optional<Workspace> findByPublicSlugAndStatus(String publicSlug, WorkspaceStatus status);
}
