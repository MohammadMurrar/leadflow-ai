package com.mohammadmurrar.leadflow.publicapi.api;

import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import com.mohammadmurrar.leadflow.publicapi.PublicWorkspaceResolver;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicInquiryController {
    private final PublicInquiryService service;
    private final PublicWorkspaceResolver workspaceResolver;

    public PublicInquiryController(PublicInquiryService service, PublicWorkspaceResolver workspaceResolver) {
        this.service = service;
        this.workspaceResolver = workspaceResolver;
    }

    @GetMapping("/workspaces/{workspaceSlug}/inquiry-config")
    public ResponseEntity<PublicInquiryConfigurationResponse> workspaceConfiguration(
            @PathVariable String workspaceSlug, HttpServletRequest request) {
        requireCanonicalPath(request, workspaceSlug, "/inquiry-config");
        var workspace = workspaceResolver.resolve(workspaceSlug);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.configuration(workspace));
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

    @PostMapping(value = "/workspaces/{workspaceSlug}/leads", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublicLeadSubmissionResponse> workspaceSubmit(
            @PathVariable String workspaceSlug, @Valid @RequestBody PublicLeadRequest body,
            HttpServletRequest request) {
        requireCanonicalPath(request, workspaceSlug, "/leads");
        var workspace = workspaceResolver.resolve(workspaceSlug);
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.submit(workspace, body));
    }

    private void requireCanonicalPath(HttpServletRequest request, String workspaceSlug, String suffix) {
        String expected = request.getContextPath() + "/api/v1/public/workspaces/" + workspaceSlug + suffix;
        if (!expected.equals(request.getRequestURI())) {
            throw new NotFoundException("Public inquiry is unavailable");
        }
    }
}
