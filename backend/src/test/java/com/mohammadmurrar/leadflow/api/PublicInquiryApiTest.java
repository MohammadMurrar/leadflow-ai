package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.email.EmailOutboxRepository;
import com.mohammadmurrar.leadflow.notification.NotificationRepository;
import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptRepository;
import com.mohammadmurrar.leadflow.qualification.QualificationDispatchOutboxRepository;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest;
import com.mohammadmurrar.leadflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicInquiryApiTest {
    private static final String ACKNOWLEDGEMENT = "Thank you. Your inquiry has been received.";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired LeadRepository leads;
    @Autowired NotificationRepository notifications;
    @Autowired QualificationAttemptRepository attempts;
    @Autowired QualificationDispatchOutboxRepository outbox;
    @Autowired EmailOutboxRepository emailOutbox;
    @Autowired ServiceOfferingRepository serviceOfferings;
    @Autowired ServiceOfferingService serviceOfferingService;
    @Autowired WorkspaceSettingsService workspaceSettingsService;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    @Autowired com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository settingsRepository;
    com.mohammadmurrar.leadflow.workspace.Workspace legacyWorkspace;
    @Autowired WebApplicationContext webApplicationContext;
    @MockitoSpyBean PublicInquiryService publicInquiryService;
    MockMvc rawMockMvc;

    @BeforeEach
    void buildRawMockMvc() {
        legacyWorkspace = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "leadflow-ai", "Legacy Workspace",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE));
        settingsRepository.saveAndFlush(com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                .createNeutral(legacyWorkspace, (byte) 1));
        rawMockMvc = webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void routesRemainAdministratorProtectedAndPostRemainsCsrfProtected() throws Exception {
        mockMvc.perform(get("/api/v1/public/inquiry-config").with(anonymous()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/public/workspaces/leadflow-ai/inquiry-config").with(anonymous()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/public/inquiry-config")
                        .with(user("admin@example.invalid").roles("ADMIN")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/public/leads")
                        .with(user("admin@example.invalid").roles("ADMIN"))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        ServiceOffering offering = activeService("Secure Integration");
        mockMvc.perform(post("/api/v1/public/leads")
                        .with(user("admin@example.invalid").roles("ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("security-boundary@example.com", offering.getId(), null)))
                .andExpect(status().isAccepted());

        rawMockMvc.perform(post("/api/v1/public/leads")
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("invalid-csrf@example.com", offering.getId(), null)))
                .andExpect(status().isForbidden());
        rawMockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("missing-csrf@example.com", offering.getId(), null)))
                .andExpect(status().isForbidden());
        rawMockMvc.perform(post("/api/v1/public/leads")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("anonymous-valid@example.com", offering.getId(), null)))
                .andExpect(status().isAccepted());
        rawMockMvc.perform(post("/api/v1/public/workspaces/leadflow-ai/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("slug-missing-csrf@example.com", offering.getId(), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void slugSelectedRoutesIsolateConfigurationServicesAndCompleteOwnedGraphs() throws Exception {
        var workspaceB = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "acme-consulting", "Workspace B",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE));
        var settingsA = settingsRepository.findByWorkspaceId(legacyWorkspace.getId()).orElseThrow();
        settingsA.update("Workspace A Public", null, "A-only-description", "Brand A", null, null,
                "UTC", "USD", "One business day", null, null, null,
                List.of("a-recipient@example.invalid"));
        settingsRepository.saveAndFlush(settingsA);
        var settingsB = com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                .createNeutral(workspaceB, (byte) 2);
        settingsB.update("Workspace B Public", null, "B-only-description", "Brand B", null, null,
                "UTC", "EUR", "One business day", null, null, null,
                List.of("b-recipient@example.invalid"));
        settingsRepository.saveAndFlush(settingsB);
        ServiceOffering serviceA = activeService("Shared Service");
        ServiceOffering serviceB = serviceOfferings.saveAndFlush(
                ServiceOffering.create(workspaceB, "Shared Service", "B only"));

        String legacyConfiguration = mockMvc.perform(get("/api/v1/public/inquiry-config"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String workspaceAConfiguration = mockMvc.perform(
                        get("/api/v1/public/workspaces/leadflow-ai/inquiry-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Workspace A Public"))
                .andExpect(jsonPath("$.description").value("A-only-description"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", serviceA.getId()).exists())
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", serviceB.getId()).doesNotExist())
                .andExpect(jsonPath("$.workspaceId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(workspaceAConfiguration).isEqualTo(legacyConfiguration);

        mockMvc.perform(get("/api/v1/public/workspaces/acme-consulting/inquiry-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Workspace B Public"))
                .andExpect(jsonPath("$.description").value("B-only-description"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", serviceB.getId()).exists())
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", serviceA.getId()).doesNotExist());

        mockMvc.perform(post("/api/v1/public/workspaces/leadflow-ai/leads").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("cross-a@example.invalid", serviceB.getId(), null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/public/workspaces/acme-consulting/leads").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("workspace-b-lead@example.invalid", serviceB.getId(), null)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACKNOWLEDGEMENT));

        Lead leadB = leads.findAll().stream()
                .filter(lead -> "workspace-b-lead@example.invalid".equals(lead.getEmail()))
                .findFirst().orElseThrow();
        assertThat(leadB.getWorkspace().getId()).isEqualTo(workspaceB.getId());
        assertThat(notifications.findAll().stream().filter(item -> item.getLead().getId().equals(leadB.getId())))
                .allMatch(item -> item.getWorkspace().getId().equals(workspaceB.getId()));
        assertThat(attempts.findAll().stream().filter(item -> item.getLead().getId().equals(leadB.getId())))
                .allMatch(item -> item.getWorkspace().getId().equals(workspaceB.getId()));
        assertThat(outbox.findAll().stream().filter(item -> item.getAttempt().getLead().getId().equals(leadB.getId())))
                .allMatch(item -> item.getWorkspace().getId().equals(workspaceB.getId()));
        assertThat(emailOutbox.findAll().stream().filter(item -> item.getLead() != null
                        && item.getLead().getId().equals(leadB.getId())))
                .allMatch(item -> item.getWorkspace().getId().equals(workspaceB.getId()));
    }

    @Test
    void unavailableAndMalformedWorkspaceSlugsShareSafePublicFailure() throws Exception {
        mockMvc.perform(get("/api/v1/public/workspaces/missing/inquiry-config"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details[0]").value("serviceId: Choose an available service"));

        for (String path : List.of(
                "/api/v1/public/workspaces/UPPERCASE/inquiry-config",
                "/api/v1/public/workspaces/acme%252Fother/inquiry-config",
                "/api/v1/public/workspaces/acme;admin=true/inquiry-config")) {
            mockMvc.perform(get(path))
                    .andExpect(status().is4xxClientError())
                    .andExpect(content().string(not(containsString("Exception"))))
                    .andExpect(content().string(not(containsString("com.mohammadmurrar"))));
        }
    }

    @Test
    void onlyTheIntendedPublicInquiryMethodAndPathCombinationsAreAnonymous() throws Exception {
        List<org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> protectedRequests = List.of(
                get("/api/v1/public/leads"),
                post("/api/v1/public/inquiry-config").with(csrf()),
                get("/api/v1/public/anything"),
                post("/api/v1/public/anything").with(csrf()),
                get("/api/v1/public/inquiry-config/"),
                get("/api/v1/public/workspaces/leadflow-ai/leads"),
                post("/api/v1/public/workspaces/leadflow-ai/inquiry-config").with(csrf()),
                get("/api/v1/public/workspaces/leadflow-ai/anything"),
                get("/api/v1/leads"),
                post("/api/v1/leads").with(csrf()),
                get("/api/v1/services"),
                get("/api/v1/settings/workspace"),
                get("/api/v1/dashboard/stats"),
                get("/api/v1/analytics"),
                get("/api/v1/notifications")
        );

        for (var request : protectedRequests) {
            rawMockMvc.perform(request)
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void configurationReturnsOnlyApprovedPublicFieldsWithoutCaching() throws Exception {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(), "admin@example.invalid",
                "Administrator", UserRole.ADMIN, legacyWorkspace.getId(), null, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        try {
            var workspace = workspaceSettingsService.findWorkspace();
            workspaceSettingsService.update(new UpdateWorkspaceSettingsRequest(workspace.version(),
                    "Public Workspace", "private@example.com", "Public description",
                    "Public Brand", "Public tagline", "/assets/public-logo.svg", "Asia/Jerusalem", "ILS",
                    "We respond within two hours.", "https://example.com/privacy",
                    "We use your details to respond.", "2026-08",
                    List.of("private-recipient@example.com")));
        } finally {
            SecurityContextHolder.clearContext();
        }
        ServiceOffering active = activeService("Active Public Service");
        ServiceOffering inactive = activeService("Inactive Private Service");
        inactive.deactivate();
        serviceOfferings.flush();

        mockMvc.perform(get("/api/v1/public/inquiry-config"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.*", hasSize(11)))
                .andExpect(jsonPath("$.workspaceName").value("Public Workspace"))
                .andExpect(jsonPath("$.description").value("Public description"))
                .andExpect(jsonPath("$.publicBrandName").value("Public Brand"))
                .andExpect(jsonPath("$.publicTagline").value("Public tagline"))
                .andExpect(jsonPath("$.publicLogoPath").value("/assets/public-logo.svg"))
                .andExpect(jsonPath("$.responseTimeText").value("We respond within two hours."))
                .andExpect(jsonPath("$.privacyPolicyUrl").value("https://example.com/privacy"))
                .andExpect(jsonPath("$.privacyNoticeText").value("We use your details to respond."))
                .andExpect(jsonPath("$.privacyNoticeVersion").value("2026-08"))
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", active.getId()).exists())
                .andExpect(jsonPath("$.services[?(@.id == '%s')]", inactive.getId()).doesNotExist())
                .andExpect(jsonPath("$.services[*].*", hasSize(2)))
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.notificationRecipients").doesNotExist())
                .andExpect(jsonPath("$.timeZone").doesNotExist())
                .andExpect(jsonPath("$.currency").value("ILS"))
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andExpect(jsonPath("$.users").doesNotExist())
                .andExpect(jsonPath("$.settings").doesNotExist())
                .andExpect(jsonPath("$.automation").doesNotExist());
    }

    @Test
    void validSubmissionUsesExistingTransactionalCreationFlowAndReturnsOnlyAcknowledgement() throws Exception {
        ServiceOffering offering = activeService("Authoritative Public Service");
        long leadCount = leads.count();
        long notificationCount = notifications.count();
        long attemptCount = attempts.count();
        long outboxCount = outbox.count();

        mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("public-flow@example.com", offering.getId(), null)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.*", hasSize(1)))
                .andExpect(jsonPath("$.message").value(ACKNOWLEDGEMENT))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.qualificationScore").doesNotExist())
                .andExpect(jsonPath("$.priority").doesNotExist());

        assertThat(leads.count()).isEqualTo(leadCount + 1);
        assertThat(notifications.count()).isEqualTo(notificationCount + 1);
        assertThat(attempts.count()).isEqualTo(attemptCount + 1);
        assertThat(outbox.count()).isEqualTo(outboxCount + 1);
        Lead saved = leads.findAll().stream()
                .filter(lead -> lead.getEmail().equals("public-flow@example.com"))
                .findFirst().orElseThrow();
        assertThat(saved.getSource()).isEqualTo("public-inquiry");
        assertThat(saved.getRequestedService()).isEqualTo("Authoritative Public Service");
        assertThat(saved.getService().getId()).isEqualTo(offering.getId());
        assertThat(saved.getWorkspace()).isNotNull();
        assertThat(saved.getWorkspace().getId()).isEqualTo(legacyWorkspace.getId());
    }

    @Test
    void foreignWorkspaceServiceIsRejectedWithoutCreatingLead() throws Exception {
        var workspaceB = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                UUID.randomUUID(), "workspace-b", "Workspace B",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE));
        var foreign = serviceOfferings.saveAndFlush(ServiceOffering.create(
                workspaceB, "Foreign Service", "Not public for legacy workspace"));
        long before = leads.count();

        mockMvc.perform(post("/api/v1/public/leads").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("foreign-service@example.com", foreign.getId(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details[0]").value("serviceId: Choose an available service"));

        assertThat(leads.count()).isEqualTo(before);
    }

    @Test
    void duplicateAndHoneypotReturnIdenticalAcknowledgementsWithoutAdditionalRecords() throws Exception {
        ServiceOffering offering = activeService("Duplicate Safety Service");
        String original = postAccepted(publicLeadJson(
                "duplicate-public@example.com", offering.getId(), null));
        RecordCounts afterOriginal = counts();

        String duplicate = postAccepted(publicLeadJson(
                "  DUPLICATE-PUBLIC@EXAMPLE.COM  ", offering.getId(), null));
        assertThat(duplicate).isEqualTo(original);
        assertThat(counts()).isEqualTo(afterOriginal);

        String honeypot = postAccepted(publicLeadJson(
                "bot@example.com", offering.getId(), "https://spam.example"));
        assertThat(honeypot).isEqualTo(original);
        assertThat(counts()).isEqualTo(afterOriginal);
    }

    @Test
    void validationFailuresReturnDeterministicSafeErrorsWithoutRejectedValues() throws Exception {
        ServiceOffering offering = activeService("Validation Service");
        List<InvalidCase> cases = List.of(
                new InvalidCase("fullName", "sensitive-name", node -> node.put("fullName", "   ")),
                new InvalidCase("email", "not-an-email", node -> node.put("email", "not-an-email")),
                new InvalidCase("message", "short-secret", node -> node.put("message", "  short-secret  ")),
                new InvalidCase("serviceId", "missing-service", node -> node.remove("serviceId")),
                new InvalidCase("estimatedBudget", "-12.34", node -> node.put("estimatedBudget", -12.34)),
                new InvalidCase("desiredStartDate", "2020-01-01", node -> node.put("desiredStartDate", "2020-01-01")),
                new InvalidCase("website", "x".repeat(201), node -> node.put("website", "x".repeat(201)))
        );

        for (InvalidCase invalid : cases) {
            ObjectNode body = publicLeadNode("validation-" + invalid.field() + "@example.com",
                    offering.getId(), null);
            invalid.change().accept(body);
            mockMvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.details[0]", containsString(invalid.field() + ":")))
                    .andExpect(content().string(not(containsString(invalid.rejectedValue()))))
                    .andExpect(content().string(not(containsString("Exception"))))
                    .andExpect(content().string(not(containsString("com.mohammadmurrar"))));
        }
    }

    @Test
    void rejectsUnknownPropertiesAndMalformedJsonWithoutChangingAdministrativeDeserialization() throws Exception {
        ServiceOffering offering = activeService("Strict JSON Service");
        for (String field : List.of("source", "requestedService", "status", "qualificationScore", "arbitrary")) {
            ObjectNode body = publicLeadNode("strict-" + field + "@example.com", offering.getId(), null);
            body.put(field, "sensitive-unknown-value");
            MvcResult result = mockMvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Request validation failed"))
                    .andExpect(jsonPath("$.details").isEmpty())
                    .andExpect(content().string(not(containsString("sensitive-unknown-value"))))
                    .andReturn();
            if (!field.equals("status")) {
                assertThat(result.getResponse().getContentAsString()).doesNotContain("\"" + field + "\"");
            }
        }

        mockMvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"sensitive malformed value\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("sensitive malformed value"))))
                .andExpect(content().string(not(containsString("Json"))));

        ObjectNode administrative = objectMapper.createObjectNode();
        administrative.put("fullName", "Administrative Lead");
        administrative.put("email", "admin-unknown-compatible@example.com");
        administrative.put("requestedService", "Administrative Custom Service");
        administrative.put("message", "A sufficiently detailed administrative lead request.");
        administrative.put("source", "test");
        administrative.put("existingCompatibilityField", "still ignored");
        var principal = new com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal(
                UUID.randomUUID(), "test-admin@example.invalid", "Test Admin",
                com.mohammadmurrar.leadflow.user.UserRole.ADMIN, legacyWorkspace.getId(), null, true);
        var authenticated = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, principal.getAuthorities());
        mockMvc.perform(post("/api/v1/leads").with(authentication(authenticated))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(administrative)))
                .andExpect(status().isCreated());
    }

    @Test
    void unsupportedMediaTypesReturnSafeUnsupportedMediaTypeResponse() throws Exception {
        String submittedBody = "sensitive plain-text inquiry body";

        for (MediaType contentType : List.of(MediaType.TEXT_PLAIN, MediaType.APPLICATION_OCTET_STREAM)) {
            mockMvc.perform(post("/api/v1/public/leads")
                            .contentType(contentType)
                            .content(submittedBody))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(content().string(""))
                    .andExpect(content().string(not(containsString(submittedBody))))
                    .andExpect(content().string(not(containsString("Exception"))))
                    .andExpect(content().string(not(containsString("org.springframework"))))
                    .andExpect(content().string(not(containsString("com.mohammadmurrar"))));
        }
    }

    @Test
    void missingJsonBodyReturnsSafeBadRequestResponse() throws Exception {
        mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("org.springframework"))))
                .andExpect(content().string(not(containsString("com.mohammadmurrar"))));
    }

    @Test
    void unknownAndInactiveServicesReturnTheSameSafePublicError() throws Exception {
        ServiceOffering inactive = activeService("Unavailable Service");
        inactive.deactivate();
        serviceOfferings.flush();
        UUID unknown = UUID.fromString("99999999-9999-4999-8999-999999999999");

        JsonNode unknownError = errorBody(publicLeadJson("unknown-service@example.com", unknown, null));
        JsonNode inactiveError = errorBody(publicLeadJson("inactive-service@example.com", inactive.getId(), null));

        assertThat(unknownError.get("status").asInt()).isEqualTo(400);
        assertThat(inactiveError.get("status").asInt()).isEqualTo(400);
        assertThat(unknownError.get("message")).isEqualTo(inactiveError.get("message"));
        assertThat(unknownError.get("details")).isEqualTo(inactiveError.get("details"));
        assertThat(unknownError.get("details").get(0).asText())
                .isEqualTo("serviceId: Choose an available service");
        for (String response : List.of(unknownError.toString(), inactiveError.toString())) {
            assertThat(response).doesNotContain(unknown.toString(), inactive.getId().toString(),
                    "not found", "inactive", "Exception", "com.mohammadmurrar");
        }
    }

    @Test
    void unexpectedFailureReturnsGenericFiveHundredWithoutInternalMessage() throws Exception {
        doThrow(new IllegalStateException("sensitive database failure"))
                .when(publicInquiryService).submit(any(PublicLeadRequest.class));
        ServiceOffering offering = activeService("Failure Service");

        mockMvc.perform(post("/api/v1/public/leads").contentType(MediaType.APPLICATION_JSON)
                        .content(publicLeadJson("failure@example.com", offering.getId(), null)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").value("The inquiry could not be processed"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(not(containsString("sensitive database failure"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    private ServiceOffering activeService(String name) {
        return serviceOfferings.saveAndFlush(ServiceOffering.create(
                legacyWorkspace, name, "Private description"));
    }

    private String postAccepted(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACKNOWLEDGEMENT))
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode errorBody(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/public/leads")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String publicLeadJson(String email, UUID serviceId, String website) throws Exception {
        return objectMapper.writeValueAsString(publicLeadNode(email, serviceId, website));
    }

    private ObjectNode publicLeadNode(String email, UUID serviceId, String website) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("fullName", "Public Lead");
        node.put("email", email);
        node.put("phone", "+1 555 0100");
        node.put("company", "Public Company");
        node.put("serviceId", serviceId.toString());
        node.put("estimatedBudget", new BigDecimal("4500.00"));
        node.put("desiredStartDate", LocalDate.now().plusDays(14).toString());
        node.put("message", "We need a secure customer portal integrated with our existing workflow.");
        if (website == null) node.putNull("website"); else node.put("website", website);
        return node;
    }

    private RecordCounts counts() {
        return new RecordCounts(leads.count(), notifications.count(), attempts.count(), outbox.count());
    }

    private record RecordCounts(long leads, long notifications, long attempts, long outbox) {}
    private record InvalidCase(String field, String rejectedValue,
            java.util.function.Consumer<ObjectNode> change) {}
}
