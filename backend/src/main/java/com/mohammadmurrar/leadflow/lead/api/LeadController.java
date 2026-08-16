package com.mohammadmurrar.leadflow.lead.api;

import com.mohammadmurrar.leadflow.lead.*;
import org.springframework.data.web.PageableDefault;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
import java.util.List;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptService;
import com.mohammadmurrar.leadflow.qualification.api.*;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {
    private final LeadService service;
    private final QualificationAttemptService attemptService;

    public LeadController(LeadService service, QualificationAttemptService attemptService) {
        this.service = service;
        this.attemptService = attemptService;
    }

    @PostMapping
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody CreateLeadRequest request) {
        LeadResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/leads/" + response.id())).body(response);
    }

    @GetMapping
    public Page<LeadResponse> findAll(
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) QualificationState qualificationState,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(status, qualificationState, search, pageable);
    }

    @GetMapping("/{id}")
    public LeadResponse findOne(@PathVariable UUID id) { return service.findById(id); }

    @PatchMapping("/{id}/status")
    public LeadResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(id, request.status(), request.version());
    }

    @PostMapping("/{id}/qualification-retry")
    public QualificationOutcomeResponse retryQualification(@PathVariable UUID id,
            @Valid @RequestBody QualificationRetryRequest request) {
        return attemptService.retry(id, request.version());
    }

    @GetMapping("/{id}/qualification-attempts")
    public List<QualificationAttemptResponse> qualificationAttempts(@PathVariable UUID id) {
        return attemptService.history(id);
    }

}
