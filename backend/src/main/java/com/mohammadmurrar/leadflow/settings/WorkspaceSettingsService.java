package com.mohammadmurrar.leadflow.settings;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.qualification.QualificationReliabilityProperties;
import com.mohammadmurrar.leadflow.settings.api.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WorkspaceSettingsService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final WorkspaceSettingsRepository repository;
    private final QualificationReliabilityProperties reliability;

    public WorkspaceSettingsService(WorkspaceSettingsRepository repository,
            QualificationReliabilityProperties reliability) {
        this.repository = repository;
        this.reliability = reliability;
    }

    @Transactional
    public WorkspaceSettingsResponse findWorkspace() {
        return WorkspaceSettingsResponse.from(getOrInitialize());
    }

    @Transactional
    public WorkspaceSettingsResponse update(UpdateWorkspaceSettingsRequest request) {
        WorkspaceSettings settings = getOrInitialize();
        if (settings.getVersion() != request.version()) throw stale();
        String email = normalizeOptional(request.contactEmail());
        String description = normalizeOptional(request.description());
        if (email != null && (email.length() > 180 || !EMAIL.matcher(email).matches())) {
            throw new InvalidWorkspaceSettingsException("Contact email must be a valid email address");
        }
        if (description != null && description.length() > 500) {
            throw new InvalidWorkspaceSettingsException("Description must contain 500 characters or fewer");
        }
        try {
            if (settings.update(request.workspaceName(), email, description)) {
                repository.flush();
            }
            return WorkspaceSettingsResponse.from(settings);
        } catch (WorkspaceSettings.InvalidWorkspaceNameException ex) {
            throw new InvalidWorkspaceSettingsException("Workspace name is invalid");
        } catch (OptimisticLockingFailureException ex) {
            throw stale();
        }
    }

    @Transactional(readOnly = true)
    public AutomationStatusResponse getAutomationStatus() {
        return new AutomationStatusResponse(reliability.dispatcherEnabled(),
                reliability.legacyCallbackEnabled(), reliability.retryEnabled(), true);
    }

    private WorkspaceSettings getOrInitialize() {
        return repository.findBySingletonKey((byte) 1).orElseGet(this::initialize);
    }

    private WorkspaceSettings initialize() {
        repository.initializeIfMissing(UUID.randomUUID());
        return repository.findBySingletonKey((byte) 1).orElseThrow(() ->
                new WorkspaceSettingsUnavailableException("Workspace settings are temporarily unavailable"));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ConflictException stale() {
        return new ConflictException("Workspace settings changed elsewhere. Refresh and try again");
    }

    public static class InvalidWorkspaceSettingsException extends RuntimeException {
        public InvalidWorkspaceSettingsException(String message) { super(message); }
    }

    public static class WorkspaceSettingsUnavailableException extends RuntimeException {
        public WorkspaceSettingsUnavailableException(String message) { super(message); }
    }
}
