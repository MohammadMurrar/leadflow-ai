package com.mohammadmurrar.leadflow.qualification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QualificationDispatchOutboxRepository extends JpaRepository<QualificationDispatchOutbox, UUID> {
    @Query(value = """
            SELECT dispatch.* FROM qualification_dispatch_outbox dispatch
            JOIN workspaces workspace ON workspace.id = dispatch.workspace_id
            JOIN qualification_attempts attempt ON attempt.id = dispatch.attempt_id
            JOIN leads lead_row ON lead_row.id = attempt.lead_id
            WHERE workspace.status = 'ACTIVE'
              AND attempt.workspace_id = dispatch.workspace_id
              AND lead_row.workspace_id = dispatch.workspace_id
              AND ((dispatch.status = 'PENDING' AND dispatch.available_at <= :now)
                   OR (dispatch.status = 'IN_PROGRESS' AND dispatch.lock_expires_at <= :now))
            ORDER BY dispatch.available_at, dispatch.created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<QualificationDispatchOutbox> findClaimableForUpdate(Instant now, int batchSize);

    @Query("""
            select dispatch from QualificationDispatchOutbox dispatch
            join fetch dispatch.workspace workspace join fetch dispatch.attempt attempt
            join fetch attempt.lead lead
            where dispatch.id = :id and workspace.id = :workspaceId
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
              and attempt.workspace.id = workspace.id and lead.workspace.id = workspace.id
            """)
    java.util.Optional<QualificationDispatchOutbox> findEligibleByIdAndWorkspaceId(
            UUID id, UUID workspaceId);

    @Query("""
            select (count(dispatch) = 1) from QualificationDispatchOutbox dispatch
            where dispatch.attempt.id = :attemptId and dispatch.workspace.id = :workspaceId
              and dispatch.attempt.workspace.id = :workspaceId
              and dispatch.attempt.lead.workspace.id = :workspaceId
            """)
    boolean existsConsistentByAttemptIdAndWorkspaceId(UUID attemptId, UUID workspaceId);
}
