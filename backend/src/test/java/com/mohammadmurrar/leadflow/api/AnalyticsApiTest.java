package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.analytics.AnalyticsService;
import com.mohammadmurrar.leadflow.analytics.api.AnalyticsResponse;
import com.mohammadmurrar.leadflow.dashboard.DashboardService;
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
class AnalyticsApiTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean AnalyticsService analyticsService;

    @Test
    void omittedRangeDefaultsToThirtyAndReturnsTypedContract() throws Exception {
        when(analyticsService.getAnalytics("30")).thenReturn(response("30"));

        mockMvc.perform(get("/api/v1/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("30"))
                .andExpect(jsonPath("$.totalLeads").value(4))
                .andExpect(jsonPath("$.qualifiedLeads").value(2))
                .andExpect(jsonPath("$.qualificationRate").value(50.0))
                .andExpect(jsonPath("$.pipelineValue").value(12500.00))
                .andExpect(jsonPath("$.averageAiScore").value(82.5))
                .andExpect(jsonPath("$.performanceGranularity").value("DAILY"))
                .andExpect(jsonPath("$.performance[0].periodStart").value("2026-08-12"))
                .andExpect(jsonPath("$.statusCounts.QUALIFIED").value(2))
                .andExpect(jsonPath("$.priorityBreakdown[0].name").value("HIGH"))
                .andExpect(jsonPath("$.categoryBreakdown[0].name").value("Enterprise"))
                .andExpect(jsonPath("$.topServices[0].service").value("AI Automation"))
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(analyticsService).getAnalytics("30");
    }

    @Test
    void acceptsEverySupportedExplicitRange() throws Exception {
        for (String range : List.of("7", "30", "90", "all")) {
            when(analyticsService.getAnalytics(range)).thenReturn(response(range));
            mockMvc.perform(get("/api/v1/analytics").param("range", range))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.range").value(range));
            verify(analyticsService).getAnalytics(range);
        }
    }

    @Test
    void blankAndUnknownRangesReturnStructuredBadRequest() throws Exception {
        for (String range : List.of("", "14", "unknown")) {
            when(analyticsService.getAnalytics(range))
                    .thenThrow(new DashboardService.InvalidDashboardRangeException());

            mockMvc.perform(get("/api/v1/analytics").param("range", range))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message")
                            .value("Range must be one of: 7, 30, 90, all"))
                    .andExpect(jsonPath("$.details").isArray())
                    .andExpect(jsonPath("$.trace").doesNotExist())
                    .andExpect(jsonPath("$.exception").doesNotExist());
        }
    }

    private AnalyticsResponse response(String range) {
        return new AnalyticsResponse(
                range, 4, 2, new BigDecimal("50.0"), new BigDecimal("12500.00"),
                new BigDecimal("82.5"), AnalyticsResponse.PerformanceGranularity.DAILY,
                List.of(new AnalyticsResponse.PerformancePoint(
                        LocalDate.parse("2026-08-12"), 2, 1)),
                Map.of(LeadStatus.QUALIFIED, 2L),
                List.of(new AnalyticsResponse.BreakdownItem(
                        "HIGH", 2, new BigDecimal("50.0"))),
                List.of(new AnalyticsResponse.BreakdownItem(
                        "Enterprise", 2, new BigDecimal("100.0"))),
                List.of(new AnalyticsResponse.ServicePerformance(
                        "AI Automation", 2, 1, new BigDecimal("50.0"),
                        new BigDecimal("10000.00"), new BigDecimal("90.0"))));
    }
}
