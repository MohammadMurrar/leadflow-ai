package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ServiceApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ServiceOfferingRepository repository;
    @Autowired ServiceOfferingService offeringService;
    @Autowired LeadRepository leads;

    @Test
    void managesLifecycleWithSearchFilteringSortingPaginationAndActiveOptions() throws Exception {
        String created = mockMvc.perform(post("/api/v1/services").contentType("application/json")
                        .content(json(Map.of("name", "  AI   Automation  ", "description", "  Core service  "))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("AI Automation"))
                .andExpect(jsonPath("$.active").value(true)).andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(created);
        UUID id = UUID.fromString(node.get("id").asText());
        long version = node.get("version").asLong();

        mockMvc.perform(post("/api/v1/services").contentType("application/json")
                        .content(json(Map.of("name", "ai automation"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));

        repository.saveAndFlush(ServiceOffering.create("Data Integration", "Connect systems"));
        mockMvc.perform(get("/api/v1/services").param("search", "AUTOMATION")
                        .param("active", "true").param("page", "0").param("size", "1")
                        .param("sort", "name,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()));

        String updated = mockMvc.perform(put("/api/v1/services/{id}", id).contentType("application/json")
                        .content(json(Map.of("version", version, "name", "AI Sales Automation",
                                "description", "Updated"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("AI Sales Automation"))
                .andReturn().getResponse().getContentAsString();
        long updatedVersion = objectMapper.readTree(updated).get("version").asLong();

        String inactive = mockMvc.perform(post("/api/v1/services/{id}/deactivate", id)
                        .contentType("application/json").content(json(Map.of("version", updatedVersion))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false))
                .andReturn().getResponse().getContentAsString();
        long inactiveVersion = objectMapper.readTree(inactive).get("version").asLong();
        mockMvc.perform(post("/api/v1/services/{id}/deactivate", id).contentType("application/json")
                        .content(json(Map.of("version", updatedVersion))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(inactiveVersion));
        mockMvc.perform(get("/api/v1/services/active").param("search", "sales"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[*].id", not(hasItem(id.toString()))));

        String active = mockMvc.perform(post("/api/v1/services/{id}/reactivate", id)
                        .contentType("application/json").content(json(Map.of("version", inactiveVersion))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long activeVersion = objectMapper.readTree(active).get("version").asLong();
        mockMvc.perform(post("/api/v1/services/{id}/reactivate", id).contentType("application/json")
                        .content(json(Map.of("version", inactiveVersion))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(activeVersion));
        mockMvc.perform(get("/api/v1/services/active").param("search", "sales").param("size", "50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("AI Sales Automation"));

        mockMvc.perform(put("/api/v1/services/{id}", id).contentType("application/json")
                        .content(json(Map.of("version", 0, "name", "Stale", "description", ""))))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/services/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
        mockMvc.perform(get("/api/v1/services").param("sort", "description,asc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void leadCreationSupportsCatalogAndLegacyContractsAndRejectsInvalidSelections() throws Exception {
        ServiceOffering offering = repository.saveAndFlush(ServiceOffering.create("Authoritative Service", null));
        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("catalog-contract@example.com", offering.getId(), null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestedService").value("Authoritative Service"));
        var linked = leads.findAll().stream().filter(lead -> lead.getEmail().equals("catalog-contract@example.com"))
                .findFirst().orElseThrow();
        org.assertj.core.api.Assertions.assertThat(linked.getService().getId()).isEqualTo(offering.getId());

        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("legacy-contract@example.com", null, "Free Text Service")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.requestedService").value("Free Text Service"));

        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("both-contract@example.com", offering.getId(), "Free Text")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("neither-contract@example.com", null, null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("unknown-contract@example.com", UUID.randomUUID(), null)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));

        offeringService.deactivate(offering.getId(), offering.getVersion());
        mockMvc.perform(post("/api/v1/leads").contentType("application/json")
                        .content(leadJson("inactive-contract@example.com", offering.getId(), null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(409));
    }

    private String leadJson(String email, UUID serviceId, String requestedService) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("fullName", "Service Contract Lead");
        node.put("email", email);
        if (serviceId != null) node.put("serviceId", serviceId.toString()); else node.putNull("serviceId");
        if (requestedService != null) node.put("requestedService", requestedService); else node.putNull("requestedService");
        node.put("message", "A sufficiently detailed request for service contract testing.");
        node.put("source", "test");
        return objectMapper.writeValueAsString(node);
    }

    private String json(Object value) throws Exception { return objectMapper.writeValueAsString(value); }
}
