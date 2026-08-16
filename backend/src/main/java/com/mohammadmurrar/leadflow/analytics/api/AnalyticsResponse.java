package com.mohammadmurrar.leadflow.analytics.api;

import com.mohammadmurrar.leadflow.lead.LeadStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AnalyticsResponse(
        String range,
        long totalLeads,
        long qualifiedLeads,
        BigDecimal qualificationRate,
        BigDecimal pipelineValue,
        BigDecimal averageAiScore,
        PerformanceGranularity performanceGranularity,
        List<PerformancePoint> performance,
        Map<LeadStatus, Long> statusCounts,
        List<BreakdownItem> priorityBreakdown,
        List<BreakdownItem> categoryBreakdown,
        List<ServicePerformance> topServices
) {
    public enum PerformanceGranularity {
        DAILY,
        MONTHLY
    }

    public record PerformancePoint(
            LocalDate periodStart,
            long totalLeads,
            long qualifiedLeads
    ) {}

    public record BreakdownItem(
            String name,
            long count,
            BigDecimal percentage
    ) {}

    public record ServicePerformance(
            String service,
            long leadCount,
            long qualifiedLeads,
            BigDecimal qualificationRate,
            BigDecimal pipelineValue,
            BigDecimal averageAiScore
    ) {}
}
