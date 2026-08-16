package com.mohammadmurrar.leadflow.lead;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select lead from Lead lead where lead.id = :id")
    java.util.Optional<Lead> findByIdForUpdate(UUID id);
    Page<Lead> findByStatus(LeadStatus status, Pageable pageable);
    Page<Lead> findByStatusIn(Set<LeadStatus> statuses, Pageable pageable);
    boolean existsByEmailAndCreatedAtAfter(String email, Instant cutoff);

    @Query("""
            select lead from Lead lead
            where (:status is null or lead.status = :status)
              and (
                locate(:search, lower(lead.fullName)) > 0
                or locate(:search, lower(lead.email)) > 0
                or locate(:search, lower(coalesce(lead.phone, ''))) > 0
                or locate(:search, lower(coalesce(lead.company, ''))) > 0
                or locate(:search, lower(lead.requestedService)) > 0
              )
            """)
    Page<Lead> search(String search, LeadStatus status, Pageable pageable);

    @Query("""
            select lead from Lead lead
            where lead.status in :statuses
              and (
                locate(:search, lower(lead.fullName)) > 0
                or locate(:search, lower(lead.email)) > 0
                or locate(:search, lower(coalesce(lead.phone, ''))) > 0
                or locate(:search, lower(coalesce(lead.company, ''))) > 0
                or locate(:search, lower(lead.requestedService)) > 0
              )
            """)
    Page<Lead> searchByStatuses(String search, Set<LeadStatus> statuses, Pageable pageable);

    @Query("""
            select lead.status as status, count(lead) as leadCount
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            group by lead.status
            """)
    List<StatusCountProjection> countLeadsByStatus(Instant start, Instant end);

    @Query("""
            select sum(lead.estimatedBudget)
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
              and lead.estimatedBudget is not null
            """)
    BigDecimal sumEstimatedBudget(Instant start, Instant end);

    @Query("""
            select avg(lead.qualificationScore)
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
              and lead.qualificationScore is not null
            """)
    Double averageQualificationScore(Instant start, Instant end);

    @Query("""
            select lead.createdAt as createdAt, lead.status as status
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            """)
    List<PerformanceLeadProjection> findPerformanceLeads(Instant start, Instant end);

    @Query("select min(lead.createdAt) from Lead lead where lead.createdAt < :end")
    Instant findEarliestCreatedAtBefore(Instant end);

    @Query("""
            select lead.priority as priority, count(lead) as leadCount
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            group by lead.priority
            """)
    List<PriorityCountProjection> countLeadsByPriority(Instant start, Instant end);

    @Query("""
            select coalesce(nullif(trim(lead.category), ''), 'Unassigned') as category,
                   count(lead) as leadCount
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
              and lead.status in :successfulStatuses
            group by coalesce(nullif(trim(lead.category), ''), 'Unassigned')
            """)
    List<CategoryCountProjection> countQualifiedLeadsByCategory(
            Instant start, Instant end, Set<LeadStatus> successfulStatuses);

    @Query("""
            select year(lead.createdAt) as year, month(lead.createdAt) as month,
                   day(lead.createdAt) as day, count(lead) as leadCount,
                   sum(case when lead.status in :successfulStatuses then 1 else 0 end) as qualifiedCount
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            group by year(lead.createdAt), month(lead.createdAt), day(lead.createdAt)
            order by year(lead.createdAt), month(lead.createdAt), day(lead.createdAt)
            """)
    List<DailyPerformanceProjection> countPerformanceByDay(
            Instant start, Instant end, Set<LeadStatus> successfulStatuses);

    @Query("""
            select year(lead.createdAt) as year, month(lead.createdAt) as month,
                   count(lead) as leadCount,
                   sum(case when lead.status in :successfulStatuses then 1 else 0 end) as qualifiedCount
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            group by year(lead.createdAt), month(lead.createdAt)
            order by year(lead.createdAt), month(lead.createdAt)
            """)
    List<MonthlyPerformanceProjection> countPerformanceByMonth(
            Instant start, Instant end, Set<LeadStatus> successfulStatuses);

    @Query("""
            select coalesce(nullif(trim(lead.requestedService), ''), 'Unspecified') as service,
                   count(lead) as leadCount,
                   sum(case when lead.status in :successfulStatuses then 1 else 0 end) as qualifiedCount,
                   sum(lead.estimatedBudget) as pipelineValue,
                   avg(lead.qualificationScore) as averageAiScore
            from Lead lead
            where (:start is null or lead.createdAt >= :start) and lead.createdAt < :end
            group by coalesce(nullif(trim(lead.requestedService), ''), 'Unspecified')
            order by count(lead) desc,
                     coalesce(nullif(trim(lead.requestedService), ''), 'Unspecified') asc
            """)
    List<ServicePerformanceProjection> findTopServicePerformance(
            Instant start, Instant end, Set<LeadStatus> successfulStatuses, Pageable pageable);

    interface StatusCountProjection {
        LeadStatus getStatus();
        long getLeadCount();
    }

    interface PerformanceLeadProjection {
        Instant getCreatedAt();
        LeadStatus getStatus();
    }

    interface PriorityCountProjection {
        LeadPriority getPriority();
        long getLeadCount();
    }

    interface CategoryCountProjection {
        String getCategory();
        long getLeadCount();
    }

    interface DailyPerformanceProjection {
        int getYear();
        int getMonth();
        int getDay();
        long getLeadCount();
        long getQualifiedCount();
    }

    interface MonthlyPerformanceProjection {
        int getYear();
        int getMonth();
        long getLeadCount();
        long getQualifiedCount();
    }

    interface ServicePerformanceProjection {
        String getService();
        long getLeadCount();
        long getQualifiedCount();
        BigDecimal getPipelineValue();
        Double getAverageAiScore();
    }
}
