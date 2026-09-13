package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceAdministrationIsolationApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceRepository workspaces;
    @Autowired WorkspaceSettingsRepository settings;
    @Autowired ServiceOfferingRepository services;
    @Autowired LeadRepository leads;

    private Workspace workspaceA;
    private Workspace workspaceB;
    private Workspace pending;
    private Workspace suspended;
    private WorkspaceSettings settingsA;
    private WorkspaceSettings settingsB;
    private ServiceOffering serviceA;
    private ServiceOffering serviceB;

    @BeforeEach
    void setUp() {
        workspaceA = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "leadflow-ai", "Workspace A", WorkspaceStatus.ACTIVE));
        workspaceB = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "workspace-b-" + UUID.randomUUID(), "Workspace B", WorkspaceStatus.ACTIVE));
        pending = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "pending-" + UUID.randomUUID(), "Pending", WorkspaceStatus.PENDING));
        suspended = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "suspended-" + UUID.randomUUID(), "Suspended", WorkspaceStatus.SUSPENDED));

        settingsA = settings.saveAndFlush(WorkspaceSettings.createNeutral(workspaceA, (byte) 1));
        settingsB = settings.saveAndFlush(WorkspaceSettings.createNeutral(workspaceB, (byte) 1));
        settingsA.update("Workspace A", null, null, null, null, null, "UTC", "USD",
                "A response", null, null, null, List.of("a@example.invalid"));
        settingsB.update("Workspace B", null, null, null, null, null, "UTC", "ILS",
                "B response", null, null, null, List.of("b@example.invalid"));
        settings.flush();

        serviceA = services.saveAndFlush(ServiceOffering.create(workspaceA, "Consulting", "A service"));
        serviceB = services.saveAndFlush(ServiceOffering.create(workspaceB, "Consulting", "B service"));
    }

    @Test
    void settingsAndRecipientReplacementRemainInsideAuthenticatedWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/settings/workspace").with(user(principal(workspaceA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Workspace A"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.notificationRecipients[0]").value("a@example.invalid"))
                .andExpect(jsonPath("$.workspaceId").doesNotExist())
                .andExpect(jsonPath("$.workspace").doesNotExist());
        mockMvc.perform(get("/api/v1/settings/workspace").with(user(principal(workspaceB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Workspace B"))
                .andExpect(jsonPath("$.currency").value("ILS"))
                .andExpect(jsonPath("$.notificationRecipients[0]").value("b@example.invalid"));

        mockMvc.perform(put("/api/v1/settings/workspace")
                        .with(user(principal(workspaceA))).with(csrf())
                        .contentType("application/json")
                        .content(settingsJson(settingsA.getVersion(), "Workspace A Updated",
                                List.of("replacement-a@example.invalid"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceName").value("Workspace A Updated"))
                .andExpect(jsonPath("$.currency").value("EUR"));

        WorkspaceSettings reloadedA = settings.findByWorkspaceId(workspaceA.getId()).orElseThrow();
        WorkspaceSettings reloadedB = settings.findByWorkspaceId(workspaceB.getId()).orElseThrow();
        assertThat(reloadedA.getNotificationRecipients()).containsExactly("replacement-a@example.invalid");
        assertThat(reloadedA.getCurrency()).isEqualTo("EUR");
        assertThat(reloadedA.getWorkspace().getId()).isEqualTo(workspaceA.getId());
        assertThat(reloadedB.getWorkspaceName()).isEqualTo("Workspace B");
        assertThat(reloadedB.getNotificationRecipients()).containsExactly("b@example.invalid");
        assertThat(reloadedB.getCurrency()).isEqualTo("ILS");
        assertThat(reloadedB.getWorkspace().getId()).isEqualTo(workspaceB.getId());
    }

    @Test
    void serviceReadsAndMutationsAreScopedAndForeignMatchesUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/services").with(user(principal(workspaceA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(serviceA.getId().toString()))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(serviceB.getId().toString()))));
        mockMvc.perform(get("/api/v1/services/active").with(user(principal(workspaceB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(serviceB.getId().toString()));

        String foreign = mockMvc.perform(get("/api/v1/services/{id}", serviceB.getId())
                        .with(user(principal(workspaceA))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/api/v1/services/{id}", UUID.randomUUID())
                        .with(user(principal(workspaceA))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(foreign).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknown).get("message").asText());

        mockMvc.perform(put("/api/v1/services/{id}", serviceB.getId())
                        .with(user(principal(workspaceA))).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", serviceB.getVersion(), "name", "Changed"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/services/{id}/deactivate", serviceB.getId())
                        .with(user(principal(workspaceA))).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", serviceB.getVersion()))))
                .andExpect(status().isNotFound());
        assertThat(services.findById(serviceB.getId()).orElseThrow().isActive()).isTrue();
        assertThat(services.findById(serviceB.getId()).orElseThrow().getName()).isEqualTo("Consulting");
    }

    @Test
    void tenantDuplicatesAndWorkspaceContextFailClosed() throws Exception {
        mockMvc.perform(post("/api/v1/services").with(user(principal(workspaceA))).with(csrf())
                        .contentType("application/json").content(json(Map.of("name", " consulting "))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/services").with(user(principal(workspaceA))).with(csrf())
                        .contentType("application/json").content(json(Map.of("name", "Shared Service"))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/services").with(user(principal(workspaceB))).with(csrf())
                        .contentType("application/json").content(json(Map.of("name", "Shared Service"))))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/services/{id}", serviceA.getId())
                        .with(user(principal(workspaceA))).with(csrf()).contentType("application/json")
                        .content(json(Map.of("version", serviceA.getVersion(), "name", "Consulting"))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/services").with(user(principal(workspaceB))).with(csrf())
                        .contentType("application/json").content(json(Map.of("name", "Advisory"))))
                .andExpect(status().isCreated());

        for (Workspace unavailable : List.of(pending, suspended)) {
            mockMvc.perform(get("/api/v1/services").with(user(principal(unavailable))))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/settings/workspace").with(user(principal(unavailable))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/v1/services").with(user(principal(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/settings/workspace").with(user(principal(UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leadCollectionsDetailsMutationsAndAggregatesRemainWorkspaceIsolated() throws Exception {
        Lead leadA = leads.saveAndFlush(lead(workspaceA, serviceA, "Shared Search", "shared@example.invalid"));
        Lead leadB = leads.saveAndFlush(lead(workspaceB, serviceB, "Shared Search", "shared@example.invalid"));

        mockMvc.perform(get("/api/v1/leads").param("search", "shared search")
                        .with(user(principal(workspaceA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(leadA.getId().toString()))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(leadB.getId().toString()))));

        String foreign = mockMvc.perform(get("/api/v1/leads/{id}", leadB.getId())
                        .with(user(principal(workspaceA))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/api/v1/leads/{id}", UUID.randomUUID())
                        .with(user(principal(workspaceA))))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(foreign).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknown).get("message").asText());

        mockMvc.perform(patch("/api/v1/leads/{id}/status", leadB.getId())
                        .with(user(principal(workspaceA))).with(csrf()).contentType("application/json")
                        .content(json(Map.of("status", "CONTACTED", "version", leadB.getVersion()))))
                .andExpect(status().isNotFound());
        assertThat(leads.findById(leadB.getId()).orElseThrow().getStatus()).isEqualTo(LeadStatus.QUALIFYING);

        mockMvc.perform(get("/api/v1/dashboard/stats").with(user(principal(workspaceA))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalLeads").value(1))
                .andExpect(jsonPath("$.pipelineValue").value(4000));
        mockMvc.perform(get("/api/v1/analytics").with(user(principal(workspaceB))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalLeads").value(1))
                .andExpect(jsonPath("$.pipelineValue").value(4000));
        assertThat(leads.findById(leadA.getId()).orElseThrow().getEstimatedBudget())
                .isEqualByComparingTo("4000.00");
        assertThat(leads.findById(leadB.getId()).orElseThrow().getEstimatedBudget())
                .isEqualByComparingTo("4000.00");

        for (Workspace unavailable : List.of(pending, suspended)) {
            mockMvc.perform(get("/api/v1/leads").with(user(principal(unavailable))))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/dashboard/stats").with(user(principal(unavailable))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private Lead lead(Workspace workspace, ServiceOffering service, String name, String email) {
        Lead lead = Lead.create(workspace, name, email, null, "Tenant Co", service.getName(), service,
                new BigDecimal("4000.00"), LocalDate.now().plusDays(7),
                "A sufficiently detailed tenant isolation request.", "test");
        lead.startQualification();
        return lead;
    }

    @Autowired com.mohammadmurrar.leadflow.user.UserRepository identityUsers;

    private AuthenticatedPrincipal principal(Workspace workspace) {
        String email = "isolation-" + workspace.getId() + "@example.invalid";
        var identity = identityUsers.findByNormalizedEmail(email).orElseGet(() -> identityUsers.saveAndFlush(
                com.mohammadmurrar.leadflow.user.User.createAdministrator(workspace, email,
                        "Isolation Administrator", java.util.UUID.randomUUID().toString())));
        return new AuthenticatedPrincipal(identity.getId(), identity.getNormalizedEmail(),
                identity.getDisplayName(), identity.getRole(), workspace.getId(), null, true);
    }

    private AuthenticatedPrincipal principal(UUID workspaceId) {
        return new AuthenticatedPrincipal(UUID.randomUUID(), "admin@example.invalid",
                "Administrator", UserRole.ADMIN, workspaceId, "{test}password", true);
    }

    private String settingsJson(long version, String name, List<String> recipients) throws Exception {
        return json(Map.ofEntries(
                Map.entry("version", version), Map.entry("workspaceName", name),
                Map.entry("timeZone", "UTC"), Map.entry("currency", "EUR"),
                Map.entry("responseTimeText", "Response time"),
                Map.entry("notificationRecipients", recipients)));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
