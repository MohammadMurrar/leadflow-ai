package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.dashboard.DashboardService;
import com.mohammadmurrar.leadflow.dashboard.api.DashboardStatsResponse;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DashboardApiTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean DashboardService dashboardService;

    @Test
    void omittedRangeDefaultsToThirtyAndReturnsTypedContract() throws Exception {
        when(dashboardService.getStats("30")).thenReturn(stats());

        mockMvc.perform(get("/api/v1/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLeads").value(4))
                .andExpect(jsonPath("$.qualifiedLeads").value(2))
                .andExpect(jsonPath("$.qualificationRate").value(50.0))
                .andExpect(jsonPath("$.pipelineValue").value(12500.00))
                .andExpect(jsonPath("$.averageAiScore").value(82.5))
                .andExpect(jsonPath("$.statusCounts.QUALIFYING").value(1))
                .andExpect(jsonPath("$.performance[0].date").value("2026-08-12"))
                .andExpect(jsonPath("$.performance[0].totalLeads").value(2))
                .andExpect(jsonPath("$.performance[0].qualifiedLeads").value(1))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist());

        verify(dashboardService).getStats("30");
    }

    @Test
    void explicitRangesArePassedToService() throws Exception {
        for (String range : List.of("7", "30", "90", "all")) {
            when(dashboardService.getStats(range)).thenReturn(stats());
            mockMvc.perform(get("/api/v1/dashboard/stats").param("range", range))
                    .andExpect(status().isOk());
            verify(dashboardService).getStats(range);
        }
    }

    @Test
    void invalidRangesReturnSafeStructuredBadRequest() throws Exception {
        for (String range : List.of("0", "14", "abc", "")) {
            when(dashboardService.getStats(range))
                    .thenThrow(new DashboardService.InvalidDashboardRangeException());

            mockMvc.perform(get("/api/v1/dashboard/stats").param("range", range))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("Range must be one of: 7, 30, 90, all"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.exception").doesNotExist());
        }
    }

    private DashboardStatsResponse stats() {
        return new DashboardStatsResponse(
                4, 2, new BigDecimal("50.0"), new BigDecimal("12500.00"),
                new BigDecimal("82.5"), Map.of(
                        LeadStatus.NEW, 1L,
                        LeadStatus.QUALIFYING, 1L,
                        LeadStatus.QUALIFIED, 2L),
                List.of(new DashboardStatsResponse.PerformancePoint(
                        LocalDate.parse("2026-08-12"), 2, 1)));
    }
}
