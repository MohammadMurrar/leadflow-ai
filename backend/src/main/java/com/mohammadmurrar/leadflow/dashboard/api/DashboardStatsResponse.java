package com.mohammadmurrar.leadflow.dashboard.api;

import com.mohammadmurrar.leadflow.lead.LeadStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record DashboardStatsResponse(
        long totalLeads,
        long qualifiedLeads,
        BigDecimal qualificationRate,
        BigDecimal pipelineValue,
        BigDecimal averageAiScore,
        Map<LeadStatus, Long> statusCounts,
        List<PerformancePoint> performance
) {
    public record PerformancePoint(
            LocalDate date,
            long totalLeads,
            long qualifiedLeads
    ) {}
}
