package com.mohammadmurrar.leadflow.publicapi;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.lead.DuplicateLeadException;
import com.mohammadmurrar.leadflow.lead.LeadService;
import com.mohammadmurrar.leadflow.lead.api.CreateLeadRequest;
import com.mohammadmurrar.leadflow.publicapi.api.PublicInquiryConfigurationResponse;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadSubmissionResponse;
import com.mohammadmurrar.leadflow.publicapi.api.PublicServiceResponse;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.service.api.ServiceOptionResponse;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import com.mohammadmurrar.leadflow.settings.api.WorkspaceSettingsResponse;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicInquiryServiceTest {
    private static final String ACKNOWLEDGEMENT = "Thank you. Your inquiry has been received.";
    private static final UUID SERVICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock WorkspaceSettingsService workspaceSettingsService;
    @Mock ServiceOfferingService serviceOfferingService;
    @Mock LeadService leadService;
    @Mock LegacyPublicWorkspaceResolver workspaceResolver;

    private final Workspace workspace = Workspace.create(UUID.randomUUID(), "leadflow-ai",
            "Legacy", WorkspaceStatus.ACTIVE);

    @Test
    void configurationMapsOnlyPublicWorkspaceAndActiveServiceFieldsWithBoundedDeterministicRead() {
        PublicInquiryService service = service();
        when(workspaceResolver.resolve()).thenReturn(workspace);
        when(workspaceSettingsService.findWorkspace(workspace)).thenReturn(new WorkspaceSettingsResponse(
                7, "Northstar Services", "private@example.com", "Public workspace description",
                "Northstar Public", "Build with confidence", "/assets/northstar.svg",
                "Asia/Jerusalem", "ILS", "We respond within two hours.",
                "https://example.com/privacy", "We use your details to respond.", "2026-08",
                List.of("private-recipient@example.com"),
                Instant.parse("2026-08-22T08:00:00Z")));
        List<ServiceOptionResponse> activeServices = List.of(
                new ServiceOptionResponse(SERVICE_ID, "AI Automation"),
                new ServiceOptionResponse(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Data Integration"));
        when(serviceOfferingService.findActiveOptions(org.mockito.ArgumentMatchers.same(workspace), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(activeServices));

        PublicInquiryConfigurationResponse result = service.configuration();

        assertThat(result.workspaceName()).isEqualTo("Northstar Services");
        assertThat(result.description()).isEqualTo("Public workspace description");
        assertThat(result.publicBrandName()).isEqualTo("Northstar Public");
        assertThat(result.publicTagline()).isEqualTo("Build with confidence");
        assertThat(result.publicLogoPath()).isEqualTo("/assets/northstar.svg");
        assertThat(result.currency()).isEqualTo("ILS");
        assertThat(result.responseTimeText()).isEqualTo("We respond within two hours.");
        assertThat(result.privacyPolicyUrl()).isEqualTo("https://example.com/privacy");
        assertThat(result.privacyNoticeText()).isEqualTo("We use your details to respond.");
        assertThat(result.privacyNoticeVersion()).isEqualTo("2026-08");
        assertThat(result.services()).containsExactly(
                new PublicServiceResponse(SERVICE_ID, "AI Automation"),
                new PublicServiceResponse(UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Data Integration"));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(serviceOfferingService).findActiveOptions(org.mockito.ArgumentMatchers.same(workspace), isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
        assertThat(recordComponents(PublicInquiryConfigurationResponse.class))
                .containsExactly("workspaceName", "description", "publicBrandName", "publicTagline",
                        "publicLogoPath", "currency", "responseTimeText", "privacyPolicyUrl",
                        "privacyNoticeText", "privacyNoticeVersion", "services");
        assertThat(recordComponents(PublicServiceResponse.class)).containsExactly("id", "name");
    }

    @Test
    void publicContractsContainNoPrivateWorkspaceServiceOrLeadFields() {
        assertThat(recordComponents(PublicInquiryConfigurationResponse.class))
                .doesNotContain("contactEmail", "notificationRecipients", "timeZone",
                        "version", "updatedAt", "id", "users", "automationSettings");
        assertThat(recordComponents(PublicServiceResponse.class))
                .doesNotContain("description", "active", "version", "createdAt", "updatedAt", "pricing");
        assertThat(recordComponents(PublicLeadRequest.class)).containsExactly(
                "fullName", "email", "phone", "company", "serviceId", "estimatedBudget",
                "desiredStartDate", "message", "website");
        assertThat(recordComponents(PublicLeadRequest.class)).doesNotContain(
                "source", "requestedService", "status", "priority", "qualificationScore",
                "category", "aiSummary", "recommendedReply", "leadId", "version", "workspaceId");
    }

    @Test
    void configurationFallsBackToWorkspaceNameWhenPublicBrandNameIsAbsent() {
        when(workspaceResolver.resolve()).thenReturn(workspace);
        when(workspaceSettingsService.findWorkspace(workspace)).thenReturn(new WorkspaceSettingsResponse(
                0, "Fallback Workspace", null, null, null, null, null, "UTC", "USD",
                "We usually respond within one business day.", null, null, null, List.of(),
                Instant.parse("2026-08-22T08:00:00Z")));
        when(serviceOfferingService.findActiveOptions(org.mockito.ArgumentMatchers.same(workspace), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service().configuration().publicBrandName()).isEqualTo("Fallback Workspace");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankHoneypotDelegatesExactlyOnceWithAllowedFieldsAndControlledSource(String website) {
        PublicInquiryService service = service();
        PublicLeadRequest request = request(website);

        PublicLeadSubmissionResponse response = service.submit(request);

        ArgumentCaptor<CreateLeadRequest> delegated = ArgumentCaptor.forClass(CreateLeadRequest.class);
        verify(leadService).createForWorkspace(org.mockito.ArgumentMatchers.same(workspace), delegated.capture());
        CreateLeadRequest value = delegated.getValue();
        assertThat(value.fullName()).isEqualTo(request.fullName());
        assertThat(value.email()).isEqualTo(request.email());
        assertThat(value.phone()).isEqualTo(request.phone());
        assertThat(value.company()).isEqualTo(request.company());
        assertThat(value.serviceId()).isEqualTo(SERVICE_ID);
        assertThat(value.requestedService()).isNull();
        assertThat(value.estimatedBudget()).isEqualByComparingTo("4500.00");
        assertThat(value.desiredStartDate()).isEqualTo(request.desiredStartDate());
        assertThat(value.message()).isEqualTo(request.message());
        assertThat(value.source()).isEqualTo("public-inquiry");
        assertThat(response.message()).isEqualTo(ACKNOWLEDGEMENT);
    }

    @Test
    void filledHoneypotReturnsGenericAcknowledgementWithoutCallingLeadService() {
        PublicLeadSubmissionResponse response = service().submit(request("https://spam.example"));

        assertThat(response.message()).isEqualTo(ACKNOWLEDGEMENT);
        verifyNoInteractions(leadService);
    }

    @Test
    void duplicateAndHoneypotResponsesAreIdenticalToNewSubmission() {
        PublicInquiryService service = service();
        PublicLeadRequest genuine = request(null);
        PublicLeadSubmissionResponse created = service.submit(genuine);
        when(leadService.createForWorkspace(any(Workspace.class), any(CreateLeadRequest.class))).thenThrow(new DuplicateLeadException());

        PublicLeadSubmissionResponse duplicate = service.submit(genuine);
        PublicLeadSubmissionResponse honeypot = service.submit(request("filled"));

        assertThat(created).isEqualTo(new PublicLeadSubmissionResponse(ACKNOWLEDGEMENT));
        assertThat(duplicate).isEqualTo(created);
        assertThat(honeypot).isEqualTo(created);
        assertThat(created.message()).doesNotContain(
                genuine.fullName(), genuine.email(), genuine.phone(), genuine.company(), genuine.message());
    }

    @Test
    void unrelatedConflictPropagates() {
        ConflictException conflict = new ConflictException("Selected service is inactive");
        when(leadService.createForWorkspace(any(Workspace.class), any(CreateLeadRequest.class))).thenThrow(conflict);

        assertThatThrownBy(() -> service().submit(request(null))).isSameAs(conflict);
    }

    @Test
    void unexpectedExceptionPropagates() {
        IllegalStateException failure = new IllegalStateException("Database unavailable");
        when(leadService.createForWorkspace(any(Workspace.class), any(CreateLeadRequest.class))).thenThrow(failure);

        assertThatThrownBy(() -> service().submit(request(null))).isSameAs(failure);
    }

    private PublicInquiryService service() {
        org.mockito.Mockito.lenient().when(workspaceResolver.resolve()).thenReturn(workspace);
        return new PublicInquiryService(workspaceSettingsService, serviceOfferingService, leadService,
                workspaceResolver);
    }

    private PublicLeadRequest request(String website) {
        return new PublicLeadRequest("Alex Morgan", "alex@example.com", "+1 555 0100",
                "Northstar Services", SERVICE_ID, new BigDecimal("4500.00"),
                LocalDate.now().plusDays(14),
                "We need a secure customer portal API integrated with our CRM.", website);
    }

    private List<String> recordComponents(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }
}
