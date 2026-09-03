package com.mohammadmurrar.leadflow.email;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {
    Optional<EmailOutbox> findByDeduplicationKey(String deduplicationKey);

    @Query(value = """
            SELECT email.* FROM email_outbox email
            JOIN workspaces workspace ON workspace.id = email.workspace_id
            LEFT JOIN leads lead_row ON lead_row.id = email.lead_id
            LEFT JOIN password_reset_requests reset_request ON reset_request.id = email.password_reset_request_id
            LEFT JOIN users reset_user ON reset_user.id = reset_request.user_id
            WHERE workspace.status = 'ACTIVE'
              AND ((email.template_type = 'PASSWORD_RESET' AND email.lead_id IS NULL
                    AND reset_request.workspace_id = email.workspace_id
                    AND reset_user.workspace_id = email.workspace_id)
                   OR (email.template_type <> 'PASSWORD_RESET'
                    AND email.password_reset_request_id IS NULL
                    AND lead_row.workspace_id = email.workspace_id))
              AND ((email.status = 'PENDING' AND email.available_at <= :now)
                   OR (email.status = 'IN_PROGRESS' AND email.locked_until <= :now))
            ORDER BY email.available_at, email.created_at, email.id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<EmailOutbox> findClaimableForUpdate(Instant now, int batchSize);

    @Query("""
            select email from EmailOutbox email join fetch email.workspace workspace
            left join fetch email.lead lead left join fetch email.passwordResetRequest resetRequest
            where email.id = :id and workspace.id = :workspaceId
              and workspace.status = com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE
            """)
    Optional<EmailOutbox> findEligibleByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
