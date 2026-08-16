package com.mohammadmurrar.leadflow.lead.api;

import com.mohammadmurrar.leadflow.lead.LeadService;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptService;
import com.mohammadmurrar.leadflow.qualification.api.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/automation")
public class AutomationController {
    private final LeadService service;
    private final QualificationAttemptService attemptService;
    private final byte[] expectedKey;

    public AutomationController(LeadService service, QualificationAttemptService attemptService,
                                @Value("${leadflow.automation.api-key}") String apiKey) {
        this.service = service;
        this.attemptService = attemptService;
        this.expectedKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/leads/{id}/qualification")
    public LeadResponse qualify(@PathVariable UUID id,
                                @RequestHeader("X-Automation-Key") String key,
                                @Valid @RequestBody QualificationRequest request) {
        authenticate(key);
        return service.qualify(id, request);
    }

    @PostMapping("/leads/{leadId}/qualification-attempts/{attemptId}/start")
    public QualificationStartResponse start(@PathVariable UUID leadId, @PathVariable UUID attemptId,
            @RequestHeader("X-Automation-Key") String key,
            @Valid @RequestBody QualificationStartRequest request) {
        authenticate(key);
        return attemptService.start(leadId, attemptId, request);
    }

    @PostMapping("/leads/{leadId}/qualification-attempts/{attemptId}/success")
    public QualificationOutcomeResponse succeed(@PathVariable UUID leadId, @PathVariable UUID attemptId,
            @RequestHeader("X-Automation-Key") String key,
            @Valid @RequestBody QualificationSuccessRequest request) {
        authenticate(key);
        return attemptService.succeed(leadId, attemptId, request);
    }

    @PostMapping("/leads/{leadId}/qualification-attempts/{attemptId}/failure")
    public QualificationOutcomeResponse fail(@PathVariable UUID leadId, @PathVariable UUID attemptId,
            @RequestHeader("X-Automation-Key") String key,
            @Valid @RequestBody QualificationFailureRequest request) {
        authenticate(key);
        return attemptService.fail(leadId, attemptId, request);
    }

    private void authenticate(String key) {
        if (!MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid automation key");
        }
    }
}
