package com.mohammadmurrar.leadflow.qualification;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualificationAttemptRepository extends JpaRepository<QualificationAttempt, UUID> {
    @Query("""
            select attempt from QualificationAttempt attempt
            where attempt.lead.id = :leadId and attempt.workspace.id = :workspaceId
              and attempt.lead.workspace.id = :workspaceId and attempt.status in :statuses
            """)
    Optional<QualificationAttempt> findActiveByLeadAndWorkspace(
            @Param("leadId") UUID leadId, @Param("workspaceId") UUID workspaceId,
            @Param("statuses") List<QualificationAttemptStatus> statuses);

    @Query("""
            select attempt from QualificationAttempt attempt
            where attempt.lead.id = :leadId and attempt.workspace.id = :workspaceId
              and attempt.lead.workspace.id = :workspaceId
            order by attempt.attemptNumber desc
            """)
    List<QualificationAttempt> findHistoryByLeadAndWorkspace(
            @Param("leadId") UUID leadId, @Param("workspaceId") UUID workspaceId);

    @Query("""
            select attempt from QualificationAttempt attempt
            where attempt.lead.id = :leadId and attempt.workspace.id = :workspaceId
              and attempt.lead.workspace.id = :workspaceId
            order by attempt.attemptNumber desc
            limit 1
            """)
    Optional<QualificationAttempt> findLatestByLeadAndWorkspace(
            @Param("leadId") UUID leadId, @Param("workspaceId") UUID workspaceId);

    @Query("""
            select (count(attempt) > 0) from QualificationAttempt attempt
            where attempt.lead.id = :leadId
              and (attempt.workspace is null or attempt.workspace.id <> :workspaceId
                   or attempt.lead.workspace.id <> :workspaceId)
            """)
    boolean existsOwnershipMismatchForLead(
            @Param("leadId") UUID leadId, @Param("workspaceId") UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt from QualificationAttempt attempt join fetch attempt.workspace workspace
            join fetch attempt.lead lead where attempt.id = :id and workspace.id = :workspaceId
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
              and lead.workspace.id = workspace.id
            """)
    Optional<QualificationAttempt> findActiveByIdAndWorkspaceIdForUpdate(UUID id, UUID workspaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select attempt from QualificationAttempt attempt join fetch attempt.workspace workspace
            join fetch attempt.lead lead where attempt.id = :id
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
              and lead.workspace.id = workspace.id
            """)
    Optional<QualificationAttempt> findActiveByIdForUpdate(UUID id);

    @Query("""
            select attempt from QualificationAttempt attempt
            where attempt.status in :statuses and attempt.updatedAt < :cutoff
              and attempt.workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
              and attempt.lead.workspace.id = attempt.workspace.id
            order by attempt.updatedAt
            """)
    List<QualificationAttempt> findExpired(List<QualificationAttemptStatus> statuses, Instant cutoff, Pageable pageable);
}
