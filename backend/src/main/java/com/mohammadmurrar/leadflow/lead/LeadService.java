package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.api.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptService;
import com.mohammadmurrar.leadflow.qualification.QualificationReliabilityProperties;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.*;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class LeadService {
    private final LeadRepository repository;
    private final NotificationService notificationService;
    private final QualificationAttemptService qualificationAttemptService;
    private final QualificationReliabilityProperties reliabilityProperties;
    private final ServiceOfferingService serviceOfferingService;

    public LeadService(
            LeadRepository repository,
            NotificationService notificationService,
            QualificationAttemptService qualificationAttemptService,
            QualificationReliabilityProperties reliabilityProperties,
            ServiceOfferingService serviceOfferingService) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.qualificationAttemptService = qualificationAttemptService;
        this.reliabilityProperties = reliabilityProperties;
        this.serviceOfferingService = serviceOfferingService;
    }

    @Transactional
    public LeadResponse create(CreateLeadRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByEmailAndCreatedAtAfter(email, Instant.now().minus(Duration.ofHours(24)))) {
            throw new DuplicateLeadException();
        }
        String source = request.source() == null || request.source().isBlank() ? "website" : request.source();
        boolean hasServiceId = request.serviceId() != null;
        boolean hasRequestedService = request.requestedService() != null && !request.requestedService().isBlank();
        if (hasServiceId == hasRequestedService) {
            throw new InvalidLeadServiceSelectionException();
        }
        ServiceOffering serviceOffering = hasServiceId ? serviceOfferingService.getActive(request.serviceId()) : null;
        String requestedService = serviceOffering == null ? request.requestedService() : serviceOffering.getName();
        Lead lead = Lead.create(request.fullName(), email, request.phone(), request.company(),
                requestedService, serviceOffering, request.estimatedBudget(), request.desiredStartDate(),
                request.message(), source);
        lead.startQualification();
        Lead savedLead = repository.save(lead);
        notificationService.createNewLeadNotification(savedLead);
        qualificationAttemptService.createInitialAttempt(savedLead);
        return LeadResponse.from(savedLead);
    }

    public Page<LeadResponse> findAll(LeadStatus status, QualificationState qualificationState,
                                      String search, Pageable pageable) {
        if (status != null && qualificationState != null) {
            throw new InvalidLeadFilterException();
        }
        String normalizedSearch = normalizeSearch(search);
        Page<Lead> page;
        if (normalizedSearch == null) {
            if (status != null) {
                page = repository.findByStatus(status, pageable);
            } else if (qualificationState != null) {
                page = repository.findByStatusIn(qualificationState.statuses(), pageable);
            } else {
                page = repository.findAll(pageable);
            }
        } else if (qualificationState != null) {
            page = repository.searchByStatuses(normalizedSearch, qualificationState.statuses(), pageable);
        } else {
            page = repository.search(normalizedSearch, status, pageable);
        }
        return page.map(LeadResponse::from);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String normalized = search.trim();
        if (normalized.length() > 100) {
            throw new InvalidLeadSearchException();
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public LeadResponse findById(UUID id) {
        return LeadResponse.from(get(id));
    }

    @Transactional
    public LeadResponse changeStatus(UUID id, LeadStatus status, long version) {
        Lead lead = get(id);
        if (lead.getVersion() != version) {
            throw new ConflictException("Lead changed elsewhere. Refresh and try again");
        }
        try {
            if (!lead.transitionTo(status)) {
                return LeadResponse.from(lead);
            }
            repository.flush();
        } catch (Lead.InvalidStatusTransitionException ex) {
            throw new ConflictException(ex.getMessage());
        } catch (OptimisticLockingFailureException ex) {
            throw new ConflictException("Lead changed elsewhere. Refresh and try again");
        }
        return LeadResponse.from(lead);
    }

    @Transactional
    public LeadResponse qualify(UUID id, QualificationRequest request) {
        if (!reliabilityProperties.legacyCallbackEnabled()) {
            throw new NotFoundException("Automation endpoint is not available");
        }
        Lead lead = get(id);
        if (lead.hasSuccessfullyQualified()) {
            return LeadResponse.from(lead);
        }
        lead.applyQualification(request.score(), request.priority(), request.category(),
                request.summary(), request.recommendedReply());
        repository.flush();
        notificationService.createQualificationNotification(lead);
        return LeadResponse.from(lead);
    }

    private Lead get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Lead not found: " + id));
    }

    public static class InvalidLeadSearchException extends RuntimeException {
        public InvalidLeadSearchException() {
            super("Search must not exceed 100 characters");
        }
    }

    public static class InvalidLeadFilterException extends RuntimeException {
        public InvalidLeadFilterException() {
            super("Status and qualificationState cannot be used together");
        }
    }

    public static class InvalidLeadServiceSelectionException extends RuntimeException {
        public InvalidLeadServiceSelectionException() {
            super("Supply exactly one of serviceId or requestedService");
        }
    }
}
