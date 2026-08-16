package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.time.Instant;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeadSearchApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired LeadRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;
    private int creationOrder;

    @BeforeEach
    void setUp() {
        creationOrder = 0;
        save("Mohammad Murrar", "mohammad@example.com", "+972 50 123 4567",
                "NovaBridge Consulting", "AI Lead Automation", LeadStatus.QUALIFIED);
        save("Alex Morgan", "alex@example.com", null,
                null, "Backend API", LeadStatus.QUALIFYING);
        save("Literal Symbols", "symbols@example.com", "555_0100",
                "100% Real\\Quoted' Co", "Search Safety", LeadStatus.NEW);
    }

    @Test
    void omittedBlankAndWhitespaceSearchReturnNormalPage() throws Exception {
        for (String search : new String[]{null, "", "   "}) {
            var request = get("/api/v1/leads").param("size", "10");
            if (search != null) request.param("search", search);
            mockMvc.perform(request)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(3));
        }
    }

    @Test
    void searchesEverySupportedFieldCaseInsensitivelyAndPartially() throws Exception {
        assertMatch("  MoHaMm  ", "mohammad@example.com");
        assertMatch("EXAMPLE.COM", "mohammad@example.com", "alex@example.com", "symbols@example.com");
        assertMatch("123 45", "mohammad@example.com");
        assertMatch("novabridge", "mohammad@example.com");
        assertMatch("automation", "mohammad@example.com");
    }

    @Test
    void nullFieldsAreSafeAndNoMatchReturnsEmptyPage() throws Exception {
        assertMatch("backend", "alex@example.com");
        mockMvc.perform(get("/api/v1/leads").param("search", "does-not-exist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void treatsPercentUnderscoreBackslashAndQuoteAsLiteralText() throws Exception {
        assertMatch("%", "symbols@example.com");
        assertMatch("_", "symbols@example.com");
        assertMatch("\\", "symbols@example.com");
        assertMatch("'", "symbols@example.com");
    }

    @Test
    void searchComposesWithStatusBeforePagination() throws Exception {
        save("Another Automation Lead", "another@example.com", null,
                "Other Company", "Automation Review", LeadStatus.QUALIFYING);

        mockMvc.perform(get("/api/v1/leads")
                        .param("search", "automation")
                        .param("status", "QUALIFYING")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.content[0].email").value("another@example.com"));
    }

    @Test
    void filteredPaginationAndExplicitSortingDescribeFilteredResults() throws Exception {
        save("Zulu Search", "zulu@example.com", null, null, "Search Safety", LeadStatus.NEW);

        mockMvc.perform(get("/api/v1/leads")
                        .param("search", "search safety")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "fullName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].fullName").value("Literal Symbols"))
                .andExpect(jsonPath("$.content[0].version").isNumber());
    }

    @Test
    void defaultSortingIsNewestFirst() throws Exception {
        mockMvc.perform(get("/api/v1/leads").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("symbols@example.com"));
    }

    @Test
    void overlongSearchReturnsStructuredBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/leads").param("search", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Search must not exceed 100 characters"))
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    private void assertMatch(String search, String... emails) throws Exception {
        var actions = mockMvc.perform(get("/api/v1/leads")
                        .param("search", search)
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(emails.length));
        actions.andExpect(jsonPath("$.content[*].email", containsInAnyOrder(emails)));
    }

    private void save(String fullName, String email, String phone, String company,
                      String service, LeadStatus status) {
        Lead lead = Lead.create(fullName, email, phone, company, service,
                new BigDecimal("1000.00"), LocalDate.parse("2026-09-01"),
                "A sufficiently detailed request for integration testing.", "test");
        moveToStatus(lead, status);
        repository.saveAndFlush(lead);
        jdbcTemplate.update("update leads set created_at = ? where id = ?",
                Timestamp.from(Instant.parse("2026-08-13T00:00:00Z").plusSeconds(creationOrder++)), lead.getId());
        entityManager.clear();
    }

    private void moveToStatus(Lead lead, LeadStatus status) {
        if (status == LeadStatus.NEW) return;
        lead.startQualification();
        if (status == LeadStatus.QUALIFYING) return;
        lead.applyQualification(80, com.mohammadmurrar.leadflow.lead.LeadPriority.MEDIUM,
                "Qualified", "Qualified summary", "Recommended reply");
        if (status != LeadStatus.QUALIFIED) lead.transitionTo(status);
    }
}
