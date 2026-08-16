package com.mohammadmurrar.leadflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {
    boolean existsByNormalizedName(String normalizedName);
    boolean existsByNormalizedNameAndIdNot(String normalizedName, UUID id);

    @Query("""
            select offering from ServiceOffering offering
            where (:active is null or offering.active = :active)
              and (:search is null
                   or locate(:search, lower(offering.name)) > 0
                   or locate(:search, lower(coalesce(offering.description, ''))) > 0)
            """)
    Page<ServiceOffering> findManagement(String search, Boolean active, Pageable pageable);

    @Query("""
            select offering from ServiceOffering offering
            where offering.active = true
              and (:search is null or locate(:search, lower(offering.name)) > 0)
            """)
    Page<ServiceOffering> findActiveOptions(String search, Pageable pageable);
}
