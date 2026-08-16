package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadPriority;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QualificationWorkspaceApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired LeadRepository repository;

    @BeforeEach
    void setUp() {
        save("New Queue", "new.queue@example.com", "Queue Intake", LeadStatus.NEW, null);
        save("Processing Queue", "processing.queue@example.com", "Queue Automation", LeadStatus.QUALIFYING, null);
        save("Qualified Queue", "qualified.queue@example.com", "Queue Automation", LeadStatus.QUALIFIED, 91);
        save("Contacted Queue", "contacted.queue@example.com", "Queue Followup", LeadStatus.CONTACTED, 82);
        save("Won Queue", "won.queue@example.com", "Queue Success", LeadStatus.WON, 96);
        save("Lost Queue", "lost.queue@example.com", "Queue Review", LeadStatus.LOST, 70);
        save("Failed Queue", "failed.queue@example.com", "Queue Automation", LeadStatus.AUTOMATION_FAILED, null);
    }

    @Test
    void returnsEveryQualificationStateGroupBeforePagination() throws Exception {
        assertGroup("PROCESSING", 2, "NEW", "QUALIFYING");
        assertGroup("SUCCESSFULLY_QUALIFIED", 4, "QUALIFIED", "CONTACTED", "WON", "LOST");
        assertGroup("FAILED", 1, "AUTOMATION_FAILED");
    }

    @Test
    void searchComposesWithEveryGroup() throws Exception {
        assertSearch("PROCESSING", "automation", 1, "processing.queue@example.com");
        assertSearch("SUCCESSFULLY_QUALIFIED", "followup", 1, "contacted.queue@example.com");
        assertSearch("FAILED", "automation", 1, "failed.queue@example.com");
    }

    @Test
    void sortingPaginationAndTotalsApplyAfterGroupFiltering() throws Exception {
        mockMvc.perform(get("/api/v1/leads")
                        .param("qualificationState", "SUCCESSFULLY_QUALIFIED")
                        .param("sort", "qualificationScore,desc")
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].email").value("won.queue@example.com"))
                .andExpect(jsonPath("$.content[1].email").value("qualified.queue@example.com"));
    }

    @Test
    void concreteStatusFilterRemainsUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/leads").param("status", "CONTACTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("contacted.queue@example.com"));
    }

    @Test
    void rejectsConflictingAndUnknownGroupFiltersWithStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/leads")
                        .param("status", "QUALIFIED")
                        .param("qualificationState", "SUCCESSFULLY_QUALIFIED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Status and qualificationState cannot be used together"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        mockMvc.perform(get("/api/v1/leads").param("qualificationState", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request parameter contains an invalid value"));
    }

    private void assertGroup(String group, int count, String... statuses) throws Exception {
        mockMvc.perform(get("/api/v1/leads").param("qualificationState", group).param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count))
                .andExpect(jsonPath("$.content[*].status", containsInAnyOrder(statuses)));
    }

    private void assertSearch(String group, String search, int count, String email) throws Exception {
        mockMvc.perform(get("/api/v1/leads")
                        .param("qualificationState", group).param("search", search))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(count))
                .andExpect(jsonPath("$.content[*].email", everyItem(is(email))));
    }

    private void save(String name, String email, String service, LeadStatus status, Integer score) {
        Lead lead = Lead.create(name, email, null, name + " Company", service,
                new BigDecimal("1000.00"), LocalDate.parse("2026-09-01"),
                "A sufficiently detailed qualification workspace test request.", "test");
        if (status == LeadStatus.QUALIFYING) {
            lead.startQualification();
        } else if (status == LeadStatus.AUTOMATION_FAILED) {
            ReflectionTestUtils.setField(lead, "status", LeadStatus.AUTOMATION_FAILED);
        } else if (status != LeadStatus.NEW) {
            lead.startQualification();
            lead.applyQualification(score, LeadPriority.HIGH, "Qualified",
                    "Qualified summary", "Recommended reply");
            if (status != LeadStatus.QUALIFIED) lead.transitionTo(status);
        }
        repository.saveAndFlush(lead);
    }
}
