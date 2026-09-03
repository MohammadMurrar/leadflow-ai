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
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Service
public class PublicInquiryService {
    private static final int MAXIMUM_PUBLIC_SERVICES = 200;
    private static final String PUBLIC_SOURCE = "public-inquiry";

    private final WorkspaceSettingsService workspaceSettingsService;
    private final ServiceOfferingService serviceOfferingService;
    private final LeadService leadService;
    private final LegacyPublicWorkspaceResolver workspaceResolver;

    public PublicInquiryService(WorkspaceSettingsService workspaceSettingsService,
            ServiceOfferingService serviceOfferingService, LeadService leadService,
            LegacyPublicWorkspaceResolver workspaceResolver) {
        this.workspaceSettingsService = workspaceSettingsService;
        this.serviceOfferingService = serviceOfferingService;
        this.leadService = leadService;
        this.workspaceResolver = workspaceResolver;
    }

    public PublicInquiryConfigurationResponse configuration() {
        return configuration(workspaceResolver.resolve());
    }

    public PublicInquiryConfigurationResponse configuration(Workspace resolvedWorkspace) {
        var workspace = workspaceSettingsService.findWorkspace(resolvedWorkspace);
        var services = serviceOfferingService
                .findActiveOptions(resolvedWorkspace, null, PageRequest.of(0, MAXIMUM_PUBLIC_SERVICES))
                .map(service -> new PublicServiceResponse(service.id(), service.name()))
                .getContent();
        return new PublicInquiryConfigurationResponse(
                workspace.workspaceName(), workspace.description(),
                workspace.publicBrandName() == null ? workspace.workspaceName() : workspace.publicBrandName(),
                workspace.publicTagline(), workspace.publicLogoPath(), workspace.currency(),
                workspace.responseTimeText(),
                workspace.privacyPolicyUrl(), workspace.privacyNoticeText(),
                workspace.privacyNoticeVersion(), services);
    }

    public PublicLeadSubmissionResponse submit(PublicLeadRequest request) {
        return submit(workspaceResolver.resolve(), request);
    }

    public PublicLeadSubmissionResponse submit(Workspace workspace, PublicLeadRequest request) {
        if (request.website() != null && !request.website().isBlank()) {
            return PublicLeadSubmissionResponse.received();
        }
        try {
            leadService.createForWorkspace(workspace, new CreateLeadRequest(
                    request.fullName(), request.email(), request.phone(), request.company(),
                    request.serviceId(), null, request.estimatedBudget(), request.desiredStartDate(),
                    request.message(), PUBLIC_SOURCE));
        } catch (DuplicateLeadException ignored) {
            // Deliberately indistinguishable from a newly accepted inquiry.
        }
        return PublicLeadSubmissionResponse.received();
    }
}
