package com.mohammadmurrar.leadflow.qualification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface QualificationDispatchOutboxRepository extends JpaRepository<QualificationDispatchOutbox, UUID> {
    @Query(value = """
            SELECT * FROM qualification_dispatch_outbox
            WHERE (status = 'PENDING' AND available_at <= :now)
               OR (status = 'IN_PROGRESS' AND lock_expires_at <= :now)
            ORDER BY available_at, created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<QualificationDispatchOutbox> findClaimableForUpdate(Instant now, int batchSize);
}
