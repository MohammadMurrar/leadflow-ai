package com.mohammadmurrar.leadflow.passwordreset;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.*;

public interface PasswordResetRequestRepository extends
        JpaRepository<PasswordResetRequest, UUID>, PasswordResetRequestTokenLookup {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from PasswordResetRequest request where request.id = :id")
    Optional<PasswordResetRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select request from PasswordResetRequest request join fetch request.workspace workspace
            join fetch request.user user where request.id = :id and workspace.id = :workspaceId
              and user.workspace.id = :workspaceId
              and user.enabled = true and user.role = com.mohammadmurrar.leadflow.user.UserRole.ADMIN
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
            """)
    Optional<PasswordResetRequest> findDeliverableByIdAndWorkspaceId(
            @Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from PasswordResetRequest request join fetch request.workspace workspace
            join fetch request.user user where request.id = :id and workspace.id = :workspaceId
              and user.workspace.id = :workspaceId
              and user.enabled = true and user.role = com.mohammadmurrar.leadflow.user.UserRole.ADMIN
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
            """)
    Optional<PasswordResetRequest> findDeliverableByIdAndWorkspaceIdForUpdate(
            @Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    // Selection is bounded and deterministic; future cleanup must reload each candidate
    // with findByIdForUpdate before mutation.
    @Query("select request from PasswordResetRequest request "
            + "where request.user.id = :userId and request.activeSlot = 1")
    Optional<PasswordResetRequest> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("select request from PasswordResetRequest request "
            + "where request.activeSlot = 1 and request.expiresAt <= :now "
            + "order by request.expiresAt, request.id")
    List<PasswordResetRequest> findExpiredActive(@Param("now") Instant now, Pageable pageable);

    @Query("select request from PasswordResetRequest request where request.activeSlot is null "
            + "and ((request.consumedAt is not null and request.consumedAt < :threshold) "
            + "or (request.supersededAt is not null and request.supersededAt < :threshold)) "
            + "order by coalesce(request.consumedAt, request.supersededAt), request.id")
    List<PasswordResetRequest> findTerminalBefore(
            @Param("threshold") Instant threshold, Pageable pageable);
}
