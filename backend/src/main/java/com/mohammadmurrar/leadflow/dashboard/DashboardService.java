package com.mohammadmurrar.leadflow.dashboard;

import com.mohammadmurrar.leadflow.dashboard.api.DashboardStatsResponse;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DashboardService {
    private static final int RATE_SCALE = 1;
    private static final int MONEY_SCALE = 2;
    private static final Set<String> SUPPORTED_RANGES = Set.of("7", "30", "90", "all");

    private final LeadRepository repository;
    private final Clock clock;

    @Autowired
    public DashboardService(LeadRepository repository) {
        this(repository, Clock.systemUTC());
    }

    DashboardService(LeadRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public DashboardStatsResponse getStats() {
        return getStats("30");
    }

    public DashboardStatsResponse getStats(String rangeValue) {
        RangeContext context = resolveRange(rangeValue);
        DashboardRange range = DashboardRange.parse(context.value());
        LocalDate today = context.today();
        Instant start = context.start();
        Instant end = context.end();

        Map<LeadStatus, Long> statusCounts = emptyStatusCounts();
        repository.countLeadsByStatus(start, end).forEach(result ->
                statusCounts.put(result.getStatus(), result.getLeadCount()));

        long totalLeads = statusCounts.values().stream().mapToLong(Long::longValue).sum();
        long qualifiedLeads = successfullyQualifiedCount(statusCounts);
        BigDecimal qualificationRate = percentage(qualifiedLeads, totalLeads);
        BigDecimal pipelineValue = scale(repository.sumEstimatedBudget(start, end), MONEY_SCALE);
        BigDecimal averageAiScore = scale(repository.averageQualificationScore(start, end), RATE_SCALE);

        LocalDate firstDate = firstPerformanceDate(range, today, end);
        List<DashboardStatsResponse.PerformancePoint> performance = performanceSeries(
                firstDate, today, repository.findPerformanceLeads(start, end));

        return new DashboardStatsResponse(totalLeads, qualifiedLeads, qualificationRate,
                pipelineValue, averageAiScore, statusCounts, performance);
    }

    public RangeContext resolveRange(String rangeValue) {
        DashboardRange range = DashboardRange.parse(rangeValue);
        LocalDate today = LocalDate.now(clock);
        Instant end = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant start = range.days == null
                ? null
                : today.minusDays(range.days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        return new RangeContext(range.value, range.days, today, start, end);
    }

    private LocalDate firstPerformanceDate(DashboardRange range, LocalDate today, Instant end) {
        if (range.days != null) {
            return today.minusDays(range.days - 1L);
        }
        Instant earliest = repository.findEarliestCreatedAtBefore(end);
        return earliest == null ? today : earliest.atZone(ZoneOffset.UTC).toLocalDate();
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
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value, int scale) {
        return (value == null ? BigDecimal.ZERO : value).setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(Double value, int scale) {
        return scale(value == null ? null : BigDecimal.valueOf(value), scale);
    }

    private List<DashboardStatsResponse.PerformancePoint> performanceSeries(
            LocalDate firstDate, LocalDate lastDate,
            List<LeadRepository.PerformanceLeadProjection> leads) {
        Map<LocalDate, DailyCounts> dailyCounts = new LinkedHashMap<>();
        for (LocalDate date = firstDate; !date.isAfter(lastDate); date = date.plusDays(1)) {
            dailyCounts.put(date, new DailyCounts());
        }

        for (LeadRepository.PerformanceLeadProjection lead : leads) {
            LocalDate date = lead.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
            DailyCounts counts = dailyCounts.get(date);
            if (counts != null) {
                counts.totalLeads++;
                if (isSuccessfullyQualified(lead.getStatus())) {
                    counts.qualifiedLeads++;
                }
            }
        }

        List<DashboardStatsResponse.PerformancePoint> performance = new ArrayList<>(dailyCounts.size());
        dailyCounts.forEach((date, counts) -> performance.add(
                new DashboardStatsResponse.PerformancePoint(
                        date, counts.totalLeads, counts.qualifiedLeads)));
        return performance;
    }

    private long successfullyQualifiedCount(Map<LeadStatus, Long> statusCounts) {
        return statusCounts.entrySet().stream()
                .filter(entry -> isSuccessfullyQualified(entry.getKey()))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private boolean isSuccessfullyQualified(LeadStatus status) {
        return status == LeadStatus.QUALIFIED
                || status == LeadStatus.CONTACTED
                || status == LeadStatus.WON
                || status == LeadStatus.LOST;
    }

    private static class DailyCounts {
        private long totalLeads;
        private long qualifiedLeads;
    }

    public record RangeContext(
            String value,
            Integer days,
            LocalDate today,
            Instant start,
            Instant end
    ) {}

    private enum DashboardRange {
        SEVEN("7", 7),
        THIRTY("30", 30),
        NINETY("90", 90),
        ALL("all", null);

        private final String value;
        private final Integer days;

        DashboardRange(String value, Integer days) {
            this.value = value;
            this.days = days;
        }

        private static DashboardRange parse(String value) {
            if (value != null && SUPPORTED_RANGES.contains(value)) {
                for (DashboardRange range : values()) {
                    if (range.value.equals(value)) {
                        return range;
                    }
                }
            }
            throw new InvalidDashboardRangeException();
        }
    }

    public static class InvalidDashboardRangeException extends RuntimeException {
        public InvalidDashboardRangeException() {
            super("Range must be one of: 7, 30, 90, all");
        }
    }
}
