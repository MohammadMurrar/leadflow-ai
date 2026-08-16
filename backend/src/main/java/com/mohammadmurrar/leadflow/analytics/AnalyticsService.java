package com.mohammadmurrar.leadflow.analytics;

import com.mohammadmurrar.leadflow.analytics.api.AnalyticsResponse;
import com.mohammadmurrar.leadflow.dashboard.DashboardService;
import com.mohammadmurrar.leadflow.lead.LeadPriority;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private static final int RATE_SCALE = 1;
    private static final int MONEY_SCALE = 2;
    private static final int TOP_SERVICES_LIMIT = 10;
    private static final long MAX_ALL_TIME_DAILY_DAYS = 366;
    private static final Set<LeadStatus> SUCCESSFUL_STATUSES = Set.of(
            LeadStatus.QUALIFIED,
            LeadStatus.CONTACTED,
            LeadStatus.WON,
            LeadStatus.LOST);

    private final LeadRepository repository;
    private final DashboardService dashboardService;

    public AnalyticsService(LeadRepository repository, DashboardService dashboardService) {
        this.repository = repository;
        this.dashboardService = dashboardService;
    }

    public AnalyticsResponse getAnalytics(String rangeValue) {
        DashboardService.RangeContext range = dashboardService.resolveRange(rangeValue);

        Map<LeadStatus, Long> statusCounts = emptyStatusCounts();
        repository.countLeadsByStatus(range.start(), range.end()).forEach(result -> {
            if (result.getStatus() != null) {
                statusCounts.put(result.getStatus(), result.getLeadCount());
            }
        });

        long totalLeads = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long qualifiedLeads = SUCCESSFUL_STATUSES.stream()
                .mapToLong(status -> statusCounts.getOrDefault(status, 0L))
                .sum();

        PerformanceResult performance = performance(range, totalLeads);

        return new AnalyticsResponse(
                range.value(),
                totalLeads,
                qualifiedLeads,
                percentage(qualifiedLeads, totalLeads),
                money(repository.sumEstimatedBudget(range.start(), range.end())),
                score(repository.averageQualificationScore(range.start(), range.end())),
                performance.granularity(),
                performance.points(),
                statusCounts,
                priorityBreakdown(range, totalLeads),
                categoryBreakdown(range, qualifiedLeads),
                topServices(range));
    }

    private PerformanceResult performance(DashboardService.RangeContext range, long totalLeads) {
        if (totalLeads == 0) {
            return new PerformanceResult(
                    AnalyticsResponse.PerformanceGranularity.DAILY, List.of());
        }

        LocalDate firstDate = range.days() == null
                ? repository.findEarliestCreatedAtBefore(range.end()).atZone(ZoneOffset.UTC).toLocalDate()
                : range.today().minusDays(range.days() - 1L);
        boolean monthly = range.days() == null
                && ChronoUnit.DAYS.between(firstDate, range.today()) + 1 > MAX_ALL_TIME_DAILY_DAYS;

        if (monthly) {
            Map<LocalDate, Counts> counts = new LinkedHashMap<>();
            for (LocalDate month = firstDate.withDayOfMonth(1);
                 !month.isAfter(range.today().withDayOfMonth(1)); month = month.plusMonths(1)) {
                counts.put(month, new Counts());
            }
            repository.countPerformanceByMonth(range.start(), range.end(), SUCCESSFUL_STATUSES)
                    .forEach(result -> counts.put(
                            LocalDate.of(result.getYear(), result.getMonth(), 1),
                            new Counts(result.getLeadCount(), result.getQualifiedCount())));
            return new PerformanceResult(
                    AnalyticsResponse.PerformanceGranularity.MONTHLY, toPoints(counts));
        }

        Map<LocalDate, Counts> counts = new LinkedHashMap<>();
        for (LocalDate date = firstDate; !date.isAfter(range.today()); date = date.plusDays(1)) {
            counts.put(date, new Counts());
        }
        repository.countPerformanceByDay(range.start(), range.end(), SUCCESSFUL_STATUSES)
                .forEach(result -> counts.put(
                        LocalDate.of(result.getYear(), result.getMonth(), result.getDay()),
                        new Counts(result.getLeadCount(), result.getQualifiedCount())));
        return new PerformanceResult(
                AnalyticsResponse.PerformanceGranularity.DAILY, toPoints(counts));
    }

    private List<AnalyticsResponse.PerformancePoint> toPoints(Map<LocalDate, Counts> counts) {
        return counts.entrySet().stream()
                .map(entry -> new AnalyticsResponse.PerformancePoint(
                        entry.getKey(), entry.getValue().total(), entry.getValue().qualified()))
                .toList();
    }

    private List<AnalyticsResponse.BreakdownItem> priorityBreakdown(
            DashboardService.RangeContext range, long totalLeads) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("HIGH", 0L);
        counts.put("MEDIUM", 0L);
        counts.put("LOW", 0L);
        counts.put("Unassigned", 0L);
        repository.countLeadsByPriority(range.start(), range.end()).forEach(result -> {
            LeadPriority priority = result.getPriority();
            String name = priority == null || priority == LeadPriority.UNASSESSED
                    ? "Unassigned" : priority.name();
            counts.merge(name, result.getLeadCount(), Long::sum);
        });
        return counts.entrySet().stream()
                .map(entry -> new AnalyticsResponse.BreakdownItem(
                        entry.getKey(), entry.getValue(), percentage(entry.getValue(), totalLeads)))
                .toList();
    }

    private List<AnalyticsResponse.BreakdownItem> categoryBreakdown(
            DashboardService.RangeContext range, long qualifiedLeads) {
        List<AnalyticsResponse.BreakdownItem> breakdown = new ArrayList<>();
        repository.countQualifiedLeadsByCategory(
                        range.start(), range.end(), SUCCESSFUL_STATUSES).stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.getLeadCount(), left.getLeadCount());
                    return byCount != 0 ? byCount
                            : left.getCategory().compareToIgnoreCase(right.getCategory());
                })
                .forEach(result -> breakdown.add(new AnalyticsResponse.BreakdownItem(
                        result.getCategory(), result.getLeadCount(),
                        percentage(result.getLeadCount(), qualifiedLeads))));
        return breakdown;
    }

    private List<AnalyticsResponse.ServicePerformance> topServices(
            DashboardService.RangeContext range) {
        return repository.findTopServicePerformance(
                        range.start(), range.end(), SUCCESSFUL_STATUSES,
                        PageRequest.of(0, TOP_SERVICES_LIMIT)).stream()
                .map(result -> new AnalyticsResponse.ServicePerformance(
                        result.getService(),
                        result.getLeadCount(),
                        result.getQualifiedCount(),
                        percentage(result.getQualifiedCount(), result.getLeadCount()),
                        money(result.getPipelineValue()),
                        score(result.getAverageAiScore())))
                .toList();
    }

    private Map<LeadStatus, Long> emptyStatusCounts() {
        Map<LeadStatus, Long> counts = new EnumMap<>(LeadStatus.class);
        for (LeadStatus status : LeadStatus.values()) {
            counts.put(status, 0L);
        }
        return counts;
    }

    private BigDecimal percentage(long part, long whole) {
        if (whole == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal score(Double value) {
        return (value == null ? BigDecimal.ZERO : BigDecimal.valueOf(value))
                .setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private record Counts(long total, long qualified) {
        private Counts() {
            this(0, 0);
        }
    }

    private record PerformanceResult(
            AnalyticsResponse.PerformanceGranularity granularity,
            List<AnalyticsResponse.PerformancePoint> points
    ) {}
}
