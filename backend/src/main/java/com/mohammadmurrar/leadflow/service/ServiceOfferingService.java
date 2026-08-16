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

@Service
@Transactional(readOnly = true)
public class ServiceOfferingService {
    private static final Set<String> SORTS = Set.of("name:ASC", "name:DESC", "createdAt:ASC", "createdAt:DESC");
    private final ServiceOfferingRepository repository;

    public ServiceOfferingService(ServiceOfferingRepository repository) {
        this.repository = repository;
    }

    public Page<ServiceResponse> findAll(String search, Boolean active, Pageable pageable) {
        validatePageable(pageable, 100, true);
        return repository.findManagement(normalizeSearch(search), active, pageable).map(ServiceResponse::from);
    }

    public Page<ServiceOptionResponse> findActiveOptions(String search, Pageable pageable) {
        validatePageable(pageable, 200, false);
        Pageable ordered = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")));
        return repository.findActiveOptions(normalizeSearch(search), ordered).map(ServiceOptionResponse::from);
    }

    public ServiceResponse findById(UUID id) {
        return ServiceResponse.from(get(id));
    }

    @Transactional
    public ServiceResponse create(CreateServiceRequest request) {
        String key = normalizedKey(request.name());
        if (repository.existsByNormalizedName(key)) duplicate();
        try {
            return ServiceResponse.from(repository.saveAndFlush(ServiceOffering.create(request.name(), request.description())));
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("A service with this name already exists");
        }
    }

    @Transactional
    public ServiceResponse update(UUID id, UpdateServiceRequest request) {
        ServiceOffering offering = get(id);
        requireVersion(offering, request.version());
        String key = normalizedKey(request.name());
        if (repository.existsByNormalizedNameAndIdNot(key, id)) duplicate();
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
        ServiceOffering offering = get(id);
        if (!offering.isActive()) throw new ConflictException("The selected service is no longer active");
        return offering;
    }

    private ServiceResponse setActive(UUID id, long version, boolean active) {
        ServiceOffering offering = get(id);
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
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Service not found: " + id));
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
