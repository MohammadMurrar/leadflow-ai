package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.lead.DuplicateLeadException;
import com.mohammadmurrar.leadflow.lead.LeadService;
import com.mohammadmurrar.leadflow.lead.api.CreateLeadRequest;
import com.mohammadmurrar.leadflow.publicapi.api.PublicInquiryConfigurationResponse;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadSubmissionResponse;
import com.mohammadmurrar.leadflow.publicapi.api.PublicServiceResponse;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class PublicInquiryService {
    private static final int MAXIMUM_PUBLIC_SERVICES = 200;
    private static final String PUBLIC_SOURCE = "public-inquiry";

    private final WorkspaceSettingsService workspaceSettingsService;
    private final ServiceOfferingService serviceOfferingService;
    private final LeadService leadService;

    public PublicInquiryService(WorkspaceSettingsService workspaceSettingsService,
            ServiceOfferingService serviceOfferingService, LeadService leadService) {
        this.workspaceSettingsService = workspaceSettingsService;
        this.serviceOfferingService = serviceOfferingService;
        this.leadService = leadService;
    }

    public PublicInquiryConfigurationResponse configuration() {
        var workspace = workspaceSettingsService.findWorkspace();
        var services = serviceOfferingService
                .findActiveOptions(null, PageRequest.of(0, MAXIMUM_PUBLIC_SERVICES))
                .map(service -> new PublicServiceResponse(service.id(), service.name()))
                .getContent();
        return new PublicInquiryConfigurationResponse(
                workspace.workspaceName(), workspace.description(), services);
    }

    public PublicLeadSubmissionResponse submit(PublicLeadRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            return PublicLeadSubmissionResponse.received();
        }
        try {
            leadService.create(new CreateLeadRequest(
                    request.fullName(), request.email(), request.phone(), request.company(),
                    request.serviceId(), null, request.estimatedBudget(), request.desiredStartDate(),
                    request.message(), PUBLIC_SOURCE));
        } catch (DuplicateLeadException ignored) {
            // Deliberately indistinguishable from a newly accepted inquiry.
        }
        return PublicLeadSubmissionResponse.received();
    }
}
