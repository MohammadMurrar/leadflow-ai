package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceSettingsRepository repository;
    @Autowired WorkspaceRepository workspaces;

    @BeforeEach
    void singleton() {
        repository.deleteAll();
        var workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        if (!workspaces.existsById(workspace.getId())) workspaces.saveAndFlush(workspace);
    }

    @Test
    void readsUpdatesAndPreservesNoOpVersion() throws Exception {
        String initial = mockMvc.perform(get("/api/v1/settings/workspace"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workspaceName").value("My Workspace"))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.responseTimeText")
                        .value("We usually respond within one business day."))
                .andExpect(jsonPath("$.notificationRecipients").isEmpty())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.singletonKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(initial).get("version").asLong();

        String updated = mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(workspacePayload(version, "  Revenue   Lab  ",
                                "  contact@example.com  ", "  AI operations  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workspaceName").value("Revenue Lab"))
                .andExpect(jsonPath("$.contactEmail").value("contact@example.com"))
                .andExpect(jsonPath("$.description").value("AI operations"))
                .andExpect(jsonPath("$.publicBrandName").value("Revenue Public"))
                .andExpect(jsonPath("$.publicTagline").value("Trusted lead operations"))
                .andExpect(jsonPath("$.publicLogoPath").value("/assets/revenue.svg"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Jerusalem"))
                .andExpect(jsonPath("$.currency").value("ILS"))
                .andExpect(jsonPath("$.notificationRecipients[0]").value("alerts@example.com"))
                .andExpect(jsonPath("$.notificationRecipients[1]").value("owner@example.com"))
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(updated);

        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(workspacePayload(node.get("version").asLong(),
                                "Revenue Lab", "contact@example.com", "AI operations"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(node.get("version").asLong()))
                .andExpect(jsonPath("$.updatedAt").value(node.get("updatedAt").asText()));
    }

    @Test
    void rejectsInvalidValuesAndStaleVersionWithStructuredErrors() throws Exception {
        mockMvc.perform(get("/api/v1/settings/workspace")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(workspacePayload(0, " ", null, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(workspacePayload(0, "Workspace", "invalid", null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(workspacePayload(99, "Workspace", null, null))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void workspaceSettingsRemainAdministratorOnly() throws Exception {
        mockMvc.perform(get("/api/v1/settings/workspace").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/v1/settings/workspace").with(anonymous())
                        .contentType("application/json").content(json(workspacePayload(
                                0, "Workspace", null, null))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void automationStatusIsReadOnlyAndStrictlyAllowlisted() throws Exception {
        mockMvc.perform(get("/api/v1/settings/automation-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.dispatcherEnabled").isBoolean())
                .andExpect(jsonPath("$.legacyCallbackEnabled").isBoolean())
                .andExpect(jsonPath("$.retryEnabled").isBoolean())
                .andExpect(jsonPath("$.attemptTrackingAvailable").value(true))
                .andExpect(content().string(not(containsString("key"))))
                .andExpect(content().string(not(containsString("webhook"))))
                .andExpect(content().string(not(containsString("timeout"))));
        mockMvc.perform(put("/api/v1/settings/automation-status").contentType("application/json")
                .content("{}")) .andExpect(status().isMethodNotAllowed());
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }

    private Map<String, Object> workspacePayload(long version, String name, String email, String description) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("version", version);
        payload.put("workspaceName", name);
        payload.put("contactEmail", email);
        payload.put("description", description);
        payload.put("publicBrandName", "Revenue Public");
        payload.put("publicTagline", "Trusted lead operations");
        payload.put("publicLogoPath", "/assets/revenue.svg");
        payload.put("timeZone", "Asia/Jerusalem");
        payload.put("currency", "ils");
        payload.put("responseTimeText", "We respond within one business day.");
        payload.put("privacyPolicyUrl", "https://example.com/privacy");
        payload.put("privacyNoticeText", "We use your details to respond to this inquiry.");
        payload.put("privacyNoticeVersion", "2026-08");
        payload.put("notificationRecipients", List.of("Owner@Example.com", "alerts@example.com"));
        return payload;
    }
}
