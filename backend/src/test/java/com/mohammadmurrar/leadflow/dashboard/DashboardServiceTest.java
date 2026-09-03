package com.mohammadmurrar.leadflow.dashboard;

import com.mohammadmurrar.leadflow.dashboard.api.DashboardStatsResponse;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-12T10:15:30Z");
    private static final Instant END = Instant.parse("2026-08-13T00:00:00Z");
    private static final UUID WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Mock LeadRepository repository;
    @Mock CurrentWorkspace currentWorkspace;
    DashboardService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        service = new DashboardService(repository, currentWorkspace, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void defaultRangeMatchesExplicitThirtyDays() {
        Instant start = Instant.parse("2026-07-14T00:00:00Z");
        stubRange(start, END, List.of());

        DashboardStatsResponse defaultResult = service.getStats();
        DashboardStatsResponse explicitResult = service.getStats("30");

        assertThat(defaultResult).isEqualTo(explicitResult);
        assertThat(defaultResult.performance()).hasSize(30);
        assertThat(defaultResult.performance().get(0).date()).hasToString("2026-07-14");
        assertThat(defaultResult.performance().get(29).date()).hasToString("2026-08-12");
    }

    @Test
    void sevenDaysUsesInclusiveStartExclusiveTomorrowAndZeroFills() {
        Instant start = Instant.parse("2026-08-06T00:00:00Z");
        stubRange(start, END, List.of(
                performanceLead("2026-08-06T00:00:00Z", LeadStatus.QUALIFIED),
                performanceLead("2026-08-12T23:59:59.999999Z", LeadStatus.QUALIFYING)));

        DashboardStatsResponse result = service.getStats("7");

        assertThat(result.performance()).hasSize(7);
        assertThat(result.performance().get(0).totalLeads()).isEqualTo(1);
        assertThat(result.performance().get(0).qualifiedLeads()).isEqualTo(1);
        assertThat(result.performance().get(1).totalLeads()).isZero();
        assertThat(result.performance().get(6).totalLeads()).isEqualTo(1);
        verify(repository).findPerformanceLeads(WORKSPACE_ID, start, END);
        verify(repository).countLeadsByStatus(WORKSPACE_ID, start, END);
        verify(repository).sumEstimatedBudget(WORKSPACE_ID, start, END);
        verify(repository).averageQualificationScore(WORKSPACE_ID, start, END);
    }

    @Test
    void thirtyDaysUsesOneRangeForCardsStatusesAndChartAndRoundsMetrics() {
        Instant start = Instant.parse("2026-07-14T00:00:00Z");
        when(repository.countLeadsByStatus(WORKSPACE_ID, start, END)).thenReturn(List.of(
                statusCount(LeadStatus.NEW, 1),
                statusCount(LeadStatus.QUALIFIED, 2),
                statusCount(LeadStatus.CONTACTED, 1),
                statusCount(LeadStatus.WON, 1),
                statusCount(LeadStatus.LOST, 1)));
        when(repository.sumEstimatedBudget(WORKSPACE_ID, start, END)).thenReturn(new BigDecimal("123.456"));
        when(repository.averageQualificationScore(WORKSPACE_ID, start, END)).thenReturn(82.25);
        when(repository.findPerformanceLeads(WORKSPACE_ID, start, END)).thenReturn(List.of(
                performanceLead("2026-07-14T00:00:00Z", LeadStatus.QUALIFIED),
                performanceLead("2026-07-15T00:00:00Z", LeadStatus.CONTACTED),
                performanceLead("2026-07-16T00:00:00Z", LeadStatus.WON),
                performanceLead("2026-07-17T00:00:00Z", LeadStatus.LOST),
                performanceLead("2026-08-12T02:00:00Z", LeadStatus.NEW)));

        DashboardStatsResponse result = service.getStats("30");

        assertThat(result.totalLeads()).isEqualTo(6);
        assertThat(result.qualifiedLeads()).isEqualTo(5);
        assertThat(result.qualificationRate()).isEqualByComparingTo("83.3");
        assertThat(result.pipelineValue()).isEqualByComparingTo("123.46");
        assertThat(result.averageAiScore()).isEqualByComparingTo("82.3");
        assertThat(result.statusCounts()).containsEntry(LeadStatus.QUALIFIED, 2L);
        assertThat(result.statusCounts()).containsEntry(LeadStatus.CONTACTED, 1L);
        assertThat(result.statusCounts()).containsEntry(LeadStatus.WON, 1L);
        assertThat(result.statusCounts()).containsEntry(LeadStatus.LOST, 1L);
        assertThat(result.performance()).hasSize(30);
        assertThat(result.performance().stream().mapToLong(
                DashboardStatsResponse.PerformancePoint::qualifiedLeads).sum()).isEqualTo(4);
    }

    @Test
    void ninetyDaysReturnsOrderedZeroFilledSeries() {
        Instant start = Instant.parse("2026-05-15T00:00:00Z");
        stubRange(start, END, List.of());

        DashboardStatsResponse result = service.getStats("90");

        assertThat(result.performance()).hasSize(90);
        assertThat(result.performance().get(0).date()).hasToString("2026-05-15");
        assertThat(result.performance().get(89).date()).hasToString("2026-08-12");
        assertThat(result.performance()).allSatisfy(point -> assertThat(point.totalLeads()).isZero());
    }

    @Test
    void allTimeStartsAtEarliestLeadAndIncludesOldAndRecentMetrics() {
        when(repository.findEarliestCreatedAtBefore(WORKSPACE_ID, END))
                .thenReturn(Instant.parse("2025-12-30T22:00:00Z"));
        when(repository.countLeadsByStatus(WORKSPACE_ID, null, END)).thenReturn(List.of(
                statusCount(LeadStatus.QUALIFIED, 1),
                statusCount(LeadStatus.LOST, 1)));
        when(repository.sumEstimatedBudget(WORKSPACE_ID, null, END)).thenReturn(new BigDecimal("5000.00"));
        when(repository.averageQualificationScore(WORKSPACE_ID, null, END)).thenReturn(90.0);
        when(repository.findPerformanceLeads(WORKSPACE_ID, null, END)).thenReturn(List.of(
                performanceLead("2025-12-30T22:00:00Z", LeadStatus.QUALIFIED),
                performanceLead("2026-08-12T12:00:00Z", LeadStatus.LOST)));

        DashboardStatsResponse result = service.getStats("all");

        assertThat(result.totalLeads()).isEqualTo(2);
        assertThat(result.qualifiedLeads()).isEqualTo(2);
        assertThat(result.qualificationRate()).isEqualByComparingTo("100.0");
        assertThat(result.pipelineValue()).isEqualByComparingTo("5000.00");
        assertThat(result.performance().get(0).date()).hasToString("2025-12-30");
        assertThat(result.performance().get(result.performance().size() - 1).date())
                .hasToString("2026-08-12");
        assertThat(result.performance()).anySatisfy(point -> {
            if (point.date().toString().equals("2026-01-15")) {
                assertThat(point.totalLeads()).isZero();
            }
        });
    }

    @Test
    void emptyAllTimeReturnsSafeZerosAndTodayOnly() {
        stubRange(null, END, List.of());
        when(repository.findEarliestCreatedAtBefore(WORKSPACE_ID, END)).thenReturn(null);

        DashboardStatsResponse result = service.getStats("all");

        assertThat(result.totalLeads()).isZero();
        assertThat(result.qualificationRate()).isEqualByComparingTo("0.0");
        assertThat(result.pipelineValue()).isEqualByComparingTo("0.00");
        assertThat(result.averageAiScore()).isEqualByComparingTo("0.0");
        assertThat(result.statusCounts()).containsOnlyKeys(LeadStatus.values());
        assertThat(result.performance()).singleElement().satisfies(point -> {
            assertThat(point.date()).hasToString("2026-08-12");
            assertThat(point.totalLeads()).isZero();
        });
    }

    @Test
    void rejectsUnsupportedAndBlankRangesSafely() {
        for (String range : List.of("0", "14", "abc", "")) {
            assertThatThrownBy(() -> service.getStats(range))
                    .isInstanceOf(DashboardService.InvalidDashboardRangeException.class)
                    .hasMessage("Range must be one of: 7, 30, 90, all");
        }
    }

    private void stubRange(Instant start, Instant end,
                           List<LeadRepository.PerformanceLeadProjection> performance) {
        when(repository.countLeadsByStatus(WORKSPACE_ID, start, end)).thenReturn(List.of());
        when(repository.findPerformanceLeads(WORKSPACE_ID, start, end)).thenReturn(performance);
    }

    private LeadRepository.StatusCountProjection statusCount(LeadStatus status, long count) {
        return new LeadRepository.StatusCountProjection() {
            public LeadStatus getStatus() { return status; }
            public long getLeadCount() { return count; }
        };
    }

    private LeadRepository.PerformanceLeadProjection performanceLead(String createdAt, LeadStatus status) {
        return new LeadRepository.PerformanceLeadProjection() {
            public Instant getCreatedAt() { return Instant.parse(createdAt); }
            public LeadStatus getStatus() { return status; }
        };
    }
}
