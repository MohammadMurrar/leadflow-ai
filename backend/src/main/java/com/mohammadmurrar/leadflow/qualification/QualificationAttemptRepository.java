package com.mohammadmurrar.leadflow.qualification;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualificationAttemptRepository extends JpaRepository<QualificationAttempt, UUID> {
    Optional<QualificationAttempt> findByLeadIdAndStatusIn(UUID leadId, List<QualificationAttemptStatus> statuses);
    List<QualificationAttempt> findByLeadIdOrderByAttemptNumberDesc(UUID leadId);
    Optional<QualificationAttempt> findFirstByLeadIdOrderByAttemptNumberDesc(UUID leadId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from QualificationAttempt attempt join fetch attempt.lead where attempt.id = :id")
    Optional<QualificationAttempt> findByIdForUpdate(UUID id);

    @Query("""
            select attempt from QualificationAttempt attempt
            where attempt.status in :statuses and attempt.updatedAt < :cutoff
            order by attempt.updatedAt
            """)
    List<QualificationAttempt> findExpired(List<QualificationAttemptStatus> statuses, Instant cutoff, Pageable pageable);
}
