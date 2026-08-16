package com.mohammadmurrar.leadflow.settings.api;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {
    private final WorkspaceSettingsService service;

    public SettingsController(WorkspaceSettingsService service) {
        this.service = service;
    }

    @GetMapping("/workspace")
    public WorkspaceSettingsResponse workspace() {
        return service.findWorkspace();
    }

    @PutMapping("/workspace")
    public WorkspaceSettingsResponse updateWorkspace(
            @Valid @RequestBody UpdateWorkspaceSettingsRequest request) {
        return service.update(request);
    }

    @GetMapping("/automation-status")
    public AutomationStatusResponse automationStatus() {
        return service.getAutomationStatus();
    }
}
