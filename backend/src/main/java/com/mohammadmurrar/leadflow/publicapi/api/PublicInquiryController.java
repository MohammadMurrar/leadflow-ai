package com.mohammadmurrar.leadflow.publicapi.api;

import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicInquiryController {
    private final PublicInquiryService service;

    public PublicInquiryController(PublicInquiryService service) {
        this.service = service;
    }

    @GetMapping("/inquiry-config")
    public ResponseEntity<PublicInquiryConfigurationResponse> configuration() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.configuration());
    }

    @PostMapping(value = "/leads", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicLeadSubmissionResponse> submit(
            @Valid @RequestBody PublicLeadRequest request) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.submit(request));
    }
}
