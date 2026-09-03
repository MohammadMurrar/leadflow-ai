package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.api.*;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptService;
import com.mohammadmurrar.leadflow.qualification.QualificationReliabilityProperties;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.email.EmailIntentService;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;
import java.time.*;
import java.util.Locale;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@Transactional(readOnly = true)
public class LeadService {
    private final LeadRepository repository;
    private final NotificationService notificationService;
    private final QualificationAttemptService qualificationAttemptService;
    private final QualificationReliabilityProperties reliabilityProperties;
    private final ServiceOfferingService serviceOfferingService;
    private final EmailIntentService emailIntentService;
    private final CurrentWorkspace currentWorkspace;

    @Autowired
    public LeadService(
            LeadRepository repository,
            NotificationService notificationService,
            QualificationAttemptService qualificationAttemptService,
            QualificationReliabilityProperties reliabilityProperties,
            ServiceOfferingService serviceOfferingService,
            EmailIntentService emailIntentService, CurrentWorkspace currentWorkspace) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.qualificationAttemptService = qualificationAttemptService;
        this.reliabilityProperties = reliabilityProperties;
        this.serviceOfferingService = serviceOfferingService;
        this.emailIntentService = emailIntentService;
        this.currentWorkspace = currentWorkspace;
    }

    @Transactional
    public LeadResponse create(CreateLeadRequest request) {
        return create(request, currentWorkspace.requireActive());
    }

    @Transactional
    public LeadResponse createForWorkspace(Workspace workspace, CreateLeadRequest request) {
        return create(request, java.util.Objects.requireNonNull(workspace, "Workspace is required"));
    }

    private LeadResponse create(CreateLeadRequest request, Workspace workspace) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (workspace == null) throw new IllegalStateException("Workspace ownership is required");
        boolean duplicate = repository.existsByWorkspaceIdAndEmailAndCreatedAtAfter(
                workspace.getId(), email, Instant.now().minus(Duration.ofHours(24)));
        if (duplicate) {
            throw new DuplicateLeadException();
        }
        String source = request.source() == null || request.source().isBlank() ? "website" : request.source();
        boolean hasServiceId = request.serviceId() != null;
        boolean hasRequestedService = request.requestedService() != null && !request.requestedService().isBlank();
        if (hasServiceId == hasRequestedService) {
            throw new InvalidLeadServiceSelectionException();
        }
        ServiceOffering serviceOffering = hasServiceId
                ? serviceOfferingService.getActive(workspace, request.serviceId()) : null;
        String requestedService = serviceOffering == null ? request.requestedService() : serviceOffering.getName();
        Lead lead = Lead.create(workspace, request.fullName(), email, request.phone(), request.company(),
                        requestedService, serviceOffering, request.estimatedBudget(), request.desiredStartDate(),
                        request.message(), source);
        lead.startQualification();
        Lead savedLead = repository.save(lead);
        notificationService.createNewLeadNotification(savedLead);
        qualificationAttemptService.createInitialAttempt(savedLead);
        repository.flush();
        emailIntentService.enqueueNewLead(savedLead);
        return LeadResponse.from(savedLead);
    }

    public Page<LeadResponse> findAll(LeadStatus status, QualificationState qualificationState,
                                      String search, Pageable pageable) {
        if (status != null && qualificationState != null) {
            throw new InvalidLeadFilterException();
        }
        String normalizedSearch = normalizeSearch(search);
        UUID workspaceId = currentWorkspace.requireActiveId();
        Page<Lead> page;
        if (normalizedSearch == null) {
            if (status != null) {
                page = repository.findByWorkspaceIdAndStatus(workspaceId, status, pageable);
            } else if (qualificationState != null) {
                page = repository.findByWorkspaceIdAndStatusIn(workspaceId, qualificationState.statuses(), pageable);
            } else {
                page = repository.findAllByWorkspaceId(workspaceId, pageable);
            }
        } else if (qualificationState != null) {
            page = repository.searchByStatuses(workspaceId, normalizedSearch, qualificationState.statuses(), pageable);
        } else {
            page = repository.search(workspaceId, normalizedSearch, status, pageable);
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
        Lead lead = getForAutomation(id);
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
        return repository.findByIdAndWorkspaceId(id, currentWorkspace.requireActiveId())
                .orElseThrow(() -> new NotFoundException("Lead not found"));
    }

    private Lead getForAutomation(UUID id) {
        return repository.findActiveByIdForAutomation(id)
                .orElseThrow(() -> new NotFoundException("Lead not found"));
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
