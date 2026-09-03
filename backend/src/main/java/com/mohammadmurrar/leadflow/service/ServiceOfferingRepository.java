package com.mohammadmurrar.leadflow.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.UUID;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, UUID> {
    boolean existsByWorkspaceId(UUID workspaceId);
    boolean existsByWorkspaceIdAndNormalizedName(UUID workspaceId, String normalizedName);
    boolean existsByWorkspaceIdAndNormalizedNameAndIdNot(
            UUID workspaceId, String normalizedName, UUID id);
    java.util.Optional<ServiceOffering> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("""
            select offering from ServiceOffering offering
            where offering.workspace.id = :workspaceId
              and (:active is null or offering.active = :active)
              and (:search is null
                   or locate(:search, lower(offering.name)) > 0
                   or locate(:search, lower(coalesce(offering.description, ''))) > 0)
            """)
    Page<ServiceOffering> findManagementByWorkspaceId(
            UUID workspaceId, String search, Boolean active, Pageable pageable);

    @Query("""
            select offering from ServiceOffering offering
            where offering.workspace.id = :workspaceId and offering.active = true
              and (:search is null or locate(:search, lower(offering.name)) > 0)
            """)
    Page<ServiceOffering> findActiveOptionsByWorkspaceId(UUID workspaceId, String search, Pageable pageable);
}
