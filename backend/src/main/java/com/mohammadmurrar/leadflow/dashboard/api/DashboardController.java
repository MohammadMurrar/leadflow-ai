package com.mohammadmurrar.leadflow.dashboard.api;

import com.mohammadmurrar.leadflow.dashboard.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public DashboardStatsResponse getStats(
            @RequestParam(required = false) String range) {
        return service.getStats(range == null ? "30" : range);
    }
}
