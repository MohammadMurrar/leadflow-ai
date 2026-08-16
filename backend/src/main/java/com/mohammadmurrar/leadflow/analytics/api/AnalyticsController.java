package com.mohammadmurrar.leadflow.analytics.api;

import com.mohammadmurrar.leadflow.analytics.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    @GetMapping
    public AnalyticsResponse getAnalytics(
            @RequestParam(required = false) String range) {
        return service.getAnalytics(range == null ? "30" : range);
    }
}
