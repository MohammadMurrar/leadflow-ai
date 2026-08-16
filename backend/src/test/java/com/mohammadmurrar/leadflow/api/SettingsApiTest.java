package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
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

    @BeforeEach
    void singleton() {
        repository.deleteAll();
    }

    @Test
    void readsUpdatesAndPreservesNoOpVersion() throws Exception {
        String initial = mockMvc.perform(get("/api/v1/settings/workspace"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workspaceName").value("My Workspace"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.singletonKey").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long version = objectMapper.readTree(initial).get("version").asLong();

        String updated = mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(Map.of("version", version, "workspaceName", "  Revenue   Lab  ",
                                "contactEmail", "  contact@example.com  ", "description", "  AI operations  "))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workspaceName").value("Revenue Lab"))
                .andExpect(jsonPath("$.contactEmail").value("contact@example.com"))
                .andExpect(jsonPath("$.description").value("AI operations"))
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(updated);

        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(Map.of("version", node.get("version").asLong(),
                                "workspaceName", "Revenue Lab", "contactEmail", "contact@example.com",
                                "description", "AI operations"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(node.get("version").asLong()))
                .andExpect(jsonPath("$.updatedAt").value(node.get("updatedAt").asText()));
    }

    @Test
    void rejectsInvalidValuesAndStaleVersionWithStructuredErrors() throws Exception {
        mockMvc.perform(get("/api/v1/settings/workspace")).andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(Map.of("version", 0, "workspaceName", " "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(Map.of("version", 0, "workspaceName", "Workspace",
                                "contactEmail", "invalid"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(put("/api/v1/settings/workspace").contentType("application/json")
                        .content(json(Map.of("version", 99, "workspaceName", "Workspace"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
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
}
