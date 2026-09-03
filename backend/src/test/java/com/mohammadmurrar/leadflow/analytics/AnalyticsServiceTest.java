package com.mohammadmurrar.leadflow.analytics;

import com.mohammadmurrar.leadflow.analytics.api.AnalyticsResponse;
import com.mohammadmurrar.leadflow.dashboard.DashboardService;
import com.mohammadmurrar.leadflow.lead.LeadPriority;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {
    private static final Instant START = Instant.parse("2026-07-14T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-13T00:00:00Z");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock LeadRepository repository;
    @Mock DashboardService dashboardService;
    @Mock CurrentWorkspace currentWorkspace;
    AnalyticsService service;

    @BeforeEach
    void setUp() {
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        service = new AnalyticsService(repository, dashboardService, currentWorkspace);
    }

    @Test
    void calculatesAllSectionsFromOneThirtyDayRange() {
        stubThirtyDayRange();
        when(repository.countLeadsByStatus(WORKSPACE_ID, START, END)).thenReturn(List.of(
                status(LeadStatus.NEW, 1),
                status(LeadStatus.QUALIFYING, 1),
                status(LeadStatus.QUALIFIED, 2),
                status(LeadStatus.CONTACTED, 1),
                status(LeadStatus.WON, 1),
                status(LeadStatus.LOST, 1),
                status(LeadStatus.AUTOMATION_FAILED, 1)));
        when(repository.sumEstimatedBudget(WORKSPACE_ID, START, END)).thenReturn(new BigDecimal("12345.678"));
        when(repository.averageQualificationScore(WORKSPACE_ID, START, END)).thenReturn(82.25);
        when(repository.countPerformanceByDay(org.mockito.ArgumentMatchers.eq(WORKSPACE_ID),
                org.mockito.ArgumentMatchers.eq(START),
                org.mockito.ArgumentMatchers.eq(END), anySet())).thenReturn(List.of(
                day(2026, 7, 14, 2, 1), day(2026, 8, 12, 1, 1)));
        when(repository.countLeadsByPriority(WORKSPACE_ID, START, END)).thenReturn(List.of(
                priority(LeadPriority.HIGH, 2), priority(LeadPriority.MEDIUM, 1),
                priority(LeadPriority.LOW, 1), priority(LeadPriority.UNASSESSED, 3),
                priority(null, 1)));
        when(repository.countQualifiedLeadsByCategory(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.eq(START),
                org.mockito.ArgumentMatchers.eq(END),
                anySet())).thenReturn(List.of(category("Enterprise", 3), category("Unassigned", 2)));
        when(repository.findTopServicePerformance(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.eq(START),
                org.mockito.ArgumentMatchers.eq(END),
                anySet(), org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(List.of(
                service("AI Automation", 4, 3, "10000.00", 90.0),
                service("Unspecified", 2, 1, null, null)));

        AnalyticsResponse result = service.getAnalytics("30");

        assertThat(result.range()).isEqualTo("30");
        assertThat(result.totalLeads()).isEqualTo(8);
        assertThat(result.qualifiedLeads()).isEqualTo(5);
        assertThat(result.qualificationRate()).isEqualByComparingTo("62.5");
        assertThat(result.pipelineValue()).isEqualByComparingTo("12345.68");
        assertThat(result.averageAiScore()).isEqualByComparingTo("82.3");
        assertThat(result.statusCounts()).containsOnlyKeys(LeadStatus.values());
        assertThat(result.performanceGranularity())
                .isEqualTo(AnalyticsResponse.PerformanceGranularity.DAILY);
        assertThat(result.performance()).hasSize(30);
        assertThat(result.performance().get(0).totalLeads()).isEqualTo(2);
        assertThat(result.performance().get(1).totalLeads()).isZero();
        assertThat(result.performance().get(29).qualifiedLeads()).isEqualTo(1);
        assertThat(result.priorityBreakdown()).extracting(AnalyticsResponse.BreakdownItem::count)
                .containsExactly(2L, 1L, 1L, 4L);
        assertThat(result.priorityBreakdown().get(3).percentage()).isEqualByComparingTo("50.0");
        assertThat(result.categoryBreakdown()).extracting(AnalyticsResponse.BreakdownItem::name)
                .containsExactly("Enterprise", "Unassigned");
        assertThat(result.categoryBreakdown().get(0).percentage()).isEqualByComparingTo("60.0");
        assertThat(result.topServices().get(0).qualificationRate()).isEqualByComparingTo("75.0");
        assertThat(result.topServices().get(1).pipelineValue()).isEqualByComparingTo("0.00");
        assertThat(result.topServices().get(1).averageAiScore()).isEqualByComparingTo("0.0");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findTopServicePerformance(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.eq(START),
                org.mockito.ArgumentMatchers.eq(END),
                anySet(), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void zeroDataReturnsSafeMetricsAndNoImpliedPerformanceSeries() {
        stubThirtyDayRange();
        when(repository.countLeadsByStatus(WORKSPACE_ID, START, END)).thenReturn(List.of());

        AnalyticsResponse result = service.getAnalytics("30");

        assertThat(result.totalLeads()).isZero();
        assertThat(result.qualifiedLeads()).isZero();
        assertThat(result.qualificationRate()).isEqualByComparingTo("0.0");
        assertThat(result.pipelineValue()).isEqualByComparingTo("0.00");
        assertThat(result.averageAiScore()).isEqualByComparingTo("0.0");
        assertThat(result.performance()).isEmpty();
        assertThat(result.statusCounts()).containsOnlyKeys(LeadStatus.values());
        assertThat(result.priorityBreakdown()).allSatisfy(item -> {
            assertThat(item.count()).isZero();
            assertThat(item.percentage()).isEqualByComparingTo("0.0");
        });
        assertThat(result.categoryBreakdown()).isEmpty();
        assertThat(result.topServices()).isEmpty();
    }

    @Test
    void allTimeUsesNoLowerBoundAndMonthlyBucketsForLongRealSpan() {
        DashboardService.RangeContext range = new DashboardService.RangeContext(
                "all", null, LocalDate.parse("2026-08-12"), null, END);
        when(dashboardService.resolveRange("all")).thenReturn(range);
        when(repository.countLeadsByStatus(WORKSPACE_ID, null, END))
                .thenReturn(List.of(status(LeadStatus.QUALIFIED, 2)));
        when(repository.findEarliestCreatedAtBefore(WORKSPACE_ID, END))
                .thenReturn(Instant.parse("2024-01-15T10:00:00Z"));
        when(repository.countPerformanceByMonth(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(END),
                anySet())).thenReturn(List.of(
                month(2024, 1, 1, 1), month(2026, 8, 1, 1)));

        AnalyticsResponse result = service.getAnalytics("all");

        assertThat(result.performanceGranularity())
                .isEqualTo(AnalyticsResponse.PerformanceGranularity.MONTHLY);
        assertThat(result.performance().get(0).periodStart())
                .isEqualTo(LocalDate.parse("2024-01-01"));
        assertThat(result.performance().get(result.performance().size() - 1).periodStart())
                .isEqualTo(LocalDate.parse("2026-08-01"));
        assertThat(result.performance()).anySatisfy(point -> {
            if (point.periodStart().equals(LocalDate.parse("2025-01-01"))) {
                assertThat(point.totalLeads()).isZero();
            }
        });
        verify(repository).sumEstimatedBudget(WORKSPACE_ID, null, END);
        verify(repository).averageQualificationScore(WORKSPACE_ID, null, END);
        verify(repository).countLeadsByPriority(WORKSPACE_ID, null, END);
    }

    @Test
    void allTimeWithinOneYearUsesDailyBucketsFromEarliestRealLead() {
        DashboardService.RangeContext range = new DashboardService.RangeContext(
                "all", null, LocalDate.parse("2026-08-12"), null, END);
        when(dashboardService.resolveRange("all")).thenReturn(range);
        when(repository.countLeadsByStatus(WORKSPACE_ID, null, END))
                .thenReturn(List.of(status(LeadStatus.CONTACTED, 1)));
        when(repository.findEarliestCreatedAtBefore(WORKSPACE_ID, END))
                .thenReturn(Instant.parse("2026-08-10T23:00:00Z"));
        when(repository.countPerformanceByDay(
                org.mockito.ArgumentMatchers.eq(WORKSPACE_ID), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(END),
                anySet())).thenReturn(List.of(day(2026, 8, 10, 1, 1)));

        AnalyticsResponse result = service.getAnalytics("all");

        assertThat(result.performanceGranularity())
                .isEqualTo(AnalyticsResponse.PerformanceGranularity.DAILY);
        assertThat(result.performance()).hasSize(3);
        assertThat(result.performance().get(0).periodStart())
                .isEqualTo(LocalDate.parse("2026-08-10"));
    }

    private void stubThirtyDayRange() {
        when(dashboardService.resolveRange("30")).thenReturn(new DashboardService.RangeContext(
                "30", 30, LocalDate.parse("2026-08-12"), START, END));
    }

    private LeadRepository.StatusCountProjection status(LeadStatus value, long count) {
        return new LeadRepository.StatusCountProjection() {
            public LeadStatus getStatus() { return value; }
            public long getLeadCount() { return count; }
        };
    }

    private LeadRepository.PriorityCountProjection priority(LeadPriority value, long count) {
        return new LeadRepository.PriorityCountProjection() {
            public LeadPriority getPriority() { return value; }
            public long getLeadCount() { return count; }
        };
    }

    private LeadRepository.CategoryCountProjection category(String value, long count) {
        return new LeadRepository.CategoryCountProjection() {
            public String getCategory() { return value; }
            public long getLeadCount() { return count; }
        };
    }

    private LeadRepository.DailyPerformanceProjection day(
            int year, int month, int day, long count, long qualified) {
        return new LeadRepository.DailyPerformanceProjection() {
            public int getYear() { return year; }
            public int getMonth() { return month; }
            public int getDay() { return day; }
            public long getLeadCount() { return count; }
            public long getQualifiedCount() { return qualified; }
        };
    }

    private LeadRepository.MonthlyPerformanceProjection month(
            int year, int month, long count, long qualified) {
        return new LeadRepository.MonthlyPerformanceProjection() {
            public int getYear() { return year; }
            public int getMonth() { return month; }
            public long getLeadCount() { return count; }
            public long getQualifiedCount() { return qualified; }
        };
    }

    private LeadRepository.ServicePerformanceProjection service(
            String name, long count, long qualified, String pipeline, Double score) {
        return new LeadRepository.ServicePerformanceProjection() {
            public String getService() { return name; }
            public long getLeadCount() { return count; }
            public long getQualifiedCount() { return qualified; }
            public BigDecimal getPipelineValue() {
                return pipeline == null ? null : new BigDecimal(pipeline);
            }
            public Double getAverageAiScore() { return score; }
        };
    }
}
