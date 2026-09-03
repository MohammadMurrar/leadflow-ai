package com.mohammadmurrar.leadflow.service;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.service.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Transactional(readOnly = true)
public class ServiceOfferingService {
    private static final Set<String> SORTS = Set.of("name:ASC", "name:DESC", "createdAt:ASC", "createdAt:DESC");
    private final ServiceOfferingRepository repository;
    private final CurrentWorkspace currentWorkspace;

    @Autowired
    public ServiceOfferingService(ServiceOfferingRepository repository, CurrentWorkspace currentWorkspace) {
        this.repository = repository;
        this.currentWorkspace = currentWorkspace;
    }

    public Page<ServiceResponse> findAll(String search, Boolean active, Pageable pageable) {
        validatePageable(pageable, 100, true);
        UUID workspaceId = currentWorkspace.requireActiveId();
        return repository.findManagementByWorkspaceId(
                workspaceId, normalizeSearch(search), active, pageable).map(ServiceResponse::from);
    }

    public Page<ServiceOptionResponse> findActiveOptions(String search, Pageable pageable) {
        validatePageable(pageable, 200, false);
        UUID workspaceId = currentWorkspace.requireActiveId();
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        return repository.findActiveOptionsByWorkspaceId(
                workspaceId, normalizeSearch(search), ordered).map(ServiceOptionResponse::from);
    }

    public Page<ServiceOptionResponse> findActiveOptions(Workspace workspace, String search, Pageable pageable) {
        Objects.requireNonNull(workspace, "Workspace is required");
        validatePageable(pageable, 200, false);
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        return repository.findActiveOptionsByWorkspaceId(workspace.getId(), normalizeSearch(search), ordered)
                .map(ServiceOptionResponse::from);
    }

    public ServiceResponse findById(UUID id) {
        return ServiceResponse.from(get(id));
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        Workspace workspace = currentWorkspace.requireActive();
        String key = normalizedKey(request.name());
        if (repository.existsByWorkspaceIdAndNormalizedName(workspace.getId(), key)) duplicate();
        try {
            return ServiceResponse.from(repository.saveAndFlush(
                    ServiceOffering.create(workspace, request.name(), request.description())));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A service with this name already exists");
        }
    }

    @Transactional
    public ServiceResponse update(UUID id, UpdateServiceRequest request) {
        Workspace workspace = currentWorkspace.requireActive();
        ServiceOffering offering = get(workspace.getId(), id);
        requireVersion(offering, request.version());
        String key = normalizedKey(request.name());
        if (repository.existsByWorkspaceIdAndNormalizedNameAndIdNot(
                workspace.getId(), key, id)) duplicate();
        try {
            offering.update(request.name(), request.description());
            repository.flush();
            return ServiceResponse.from(offering);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A service with this name already exists");
        } catch (OptimisticLockingFailureException ex) {
            throw stale();
        }
    }

    @Transactional
    public ServiceResponse deactivate(UUID id, long version) {
        return setActive(id, version, false);
    }

    @Transactional
    public ServiceResponse reactivate(UUID id, long version) {
        return setActive(id, version, true);
    }

    public ServiceOffering getActive(UUID id) {
        ServiceOffering offering = get(currentWorkspace.requireActiveId(), id);
        if (!offering.isActive()) throw new ConflictException("The selected service is no longer active");
        return offering;
    }

    public ServiceOffering getActive(Workspace workspace, UUID id) {
        Objects.requireNonNull(workspace, "Workspace is required");
        ServiceOffering offering = repository.findByIdAndWorkspaceId(id, workspace.getId())
                .orElseThrow(() -> new NotFoundException("Service not found: " + id));
        if (!offering.isActive()) throw new ConflictException("The selected service is no longer active");
        return offering;
    }

    private ServiceResponse setActive(UUID id, long version, boolean active) {
        ServiceOffering offering = get(currentWorkspace.requireActiveId(), id);
        if (offering.isActive() == active) return ServiceResponse.from(offering);
        requireVersion(offering, version);
        try {
            if (active) offering.reactivate(); else offering.deactivate();
            repository.flush();
            return ServiceResponse.from(offering);
        } catch (OptimisticLockingFailureException ex) {
            throw stale();
        }
    }

    private ServiceOffering get(UUID id) {
        return get(currentWorkspace.requireActiveId(), id);
    }

    private ServiceOffering get(UUID workspaceId, UUID id) {
        return repository.findByIdAndWorkspaceId(id, workspaceId)
                .orElseThrow(() -> new NotFoundException("Service not found"));
    }

    private void requireVersion(ServiceOffering offering, long version) {
        if (offering.getVersion() != version) throw stale();
    }

    private ConflictException stale() {
        return new ConflictException("Service changed elsewhere. Refresh and try again");
    }

    private void duplicate() {
        throw new ConflictException("A service with this name already exists");
    }

    private String normalizedKey(String name) {
        try {
            String key = ServiceOffering.normalizedKey(name);
            if (key.length() > 120) throw new InvalidServiceRequestException(
                    "Service name must contain 120 characters or fewer after normalization");
            return key;
        } catch (ServiceOffering.InvalidServiceNameException ex) {
            throw new InvalidServiceRequestException(ex.getMessage());
        }
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String normalized = search.trim();
        if (normalized.length() > 100) throw new InvalidServiceQueryException("Search must not exceed 100 characters");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private void validatePageable(Pageable pageable, int maximumSize, boolean management) {
        if (pageable.getPageSize() > maximumSize) {
            throw new InvalidServiceQueryException("Page size must not exceed " + maximumSize);
        }
        if (!management) return;
        for (Sort.Order order : pageable.getSort()) {
            if (!SORTS.contains(order.getProperty() + ":" + order.getDirection())) {
                throw new InvalidServiceQueryException("Unsupported service sort");
            }
        }
    }

    public static class InvalidServiceQueryException extends RuntimeException {
        public InvalidServiceQueryException(String message) { super(message); }
    }

    public static class InvalidServiceRequestException extends RuntimeException {
        public InvalidServiceRequestException(String message) { super(message); }
    }
}
