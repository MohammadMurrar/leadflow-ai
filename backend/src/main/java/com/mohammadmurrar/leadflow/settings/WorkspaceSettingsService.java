package com.mohammadmurrar.leadflow.settings;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.qualification.QualificationReliabilityProperties;
import com.mohammadmurrar.leadflow.settings.api.*;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class WorkspaceSettingsService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern ENCODED_TRAVERSAL = Pattern.compile("(?i)%2e|%2f|%5c");
    private final WorkspaceSettingsRepository repository;
    private final QualificationReliabilityProperties reliability;
    private final CurrentWorkspace currentWorkspace;

    @Autowired
    public WorkspaceSettingsService(WorkspaceSettingsRepository repository,
            QualificationReliabilityProperties reliability, CurrentWorkspace currentWorkspace) {
        this.repository = repository;
        this.reliability = reliability;
        this.currentWorkspace = currentWorkspace;
    }

    @Transactional
    public WorkspaceSettingsResponse findWorkspace() {
        return WorkspaceSettingsResponse.from(getOrInitialize());
    }

    @Transactional(readOnly = true)
    public WorkspaceSettingsResponse findWorkspace(Workspace workspace) {
        Objects.requireNonNull(workspace, "Workspace is required");
        return repository.findByWorkspaceId(workspace.getId())
                .map(WorkspaceSettingsResponse::from)
                .orElseThrow(() -> new WorkspaceSettingsUnavailableException(
                        "Workspace settings are temporarily unavailable"));
    }

    @Transactional
    public WorkspaceSettingsResponse update(UpdateWorkspaceSettingsRequest request) {
        WorkspaceSettings settings = getOrInitialize();
        if (settings.getVersion() != request.version()) throw stale();
        String email = normalizeOptional(request.contactEmail());
        String description = normalizeOptional(request.description());
        String publicBrandName = normalizeOptional(request.publicBrandName());
        String publicTagline = normalizeOptional(request.publicTagline());
        String publicLogoPath = normalizeOptional(request.publicLogoPath());
        String timeZone = normalizeRequired(request.timeZone(), "Time zone is required");
        String currency = normalizeRequired(request.currency(), "Currency is required").toUpperCase(Locale.ROOT);
        String responseTimeText = normalizeRequired(request.responseTimeText(), "Response-time text is required");
        String privacyPolicyUrl = normalizeOptional(request.privacyPolicyUrl());
        String privacyNoticeText = normalizeOptional(request.privacyNoticeText());
        String privacyNoticeVersion = normalizeOptional(request.privacyNoticeVersion());
        List<String> recipients = normalizeRecipients(request.notificationRecipients());
        if (email != null && (email.length() > 180 || !EMAIL.matcher(email).matches())) {
            throw new InvalidWorkspaceSettingsException("Contact email must be a valid email address");
        }
        if (description != null && description.length() > 500) {
            throw new InvalidWorkspaceSettingsException("Description must contain 500 characters or fewer");
        }
        validateLength(publicBrandName, 120, "Public brand name");
        validateLength(publicTagline, 240, "Public tagline");
        validateLogoPath(publicLogoPath);
        validateTimeZone(timeZone);
        validateCurrency(currency);
        validateLength(responseTimeText, 240, "Response-time text");
        validatePrivacy(privacyPolicyUrl, privacyNoticeText, privacyNoticeVersion);
        try {
            if (settings.update(request.workspaceName(), email, description, publicBrandName,
                    publicTagline, publicLogoPath, timeZone, currency, responseTimeText,
                    privacyPolicyUrl, privacyNoticeText, privacyNoticeVersion, recipients)) {
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
        Workspace workspace = currentWorkspace.requireActive();
        return repository.findByWorkspaceId(workspace.getId())
                .orElseGet(() -> initialize(workspace));
    }

    private WorkspaceSettings initialize(Workspace workspace) {
        repository.initializeIfMissing(UUID.randomUUID(), workspace.getId());
        return repository.findByWorkspaceId(workspace.getId()).orElseThrow(() ->
                new WorkspaceSettingsUnavailableException("Workspace settings are temporarily unavailable"));
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeRequired(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) throw new InvalidWorkspaceSettingsException(message);
        return normalized;
    }

    private List<String> normalizeRecipients(List<String> values) {
        if (values == null) throw new InvalidWorkspaceSettingsException("Notification recipients are required");
        if (values.size() > 10) {
            throw new InvalidWorkspaceSettingsException("At most 10 notification recipients are allowed");
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            String email = normalizeOptional(value);
            if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
                throw new InvalidWorkspaceSettingsException("Notification recipient must be a valid email address");
            }
            email = email.toLowerCase(Locale.ROOT);
            if (!normalized.add(email)) {
                throw new InvalidWorkspaceSettingsException("Notification recipients must be unique");
            }
        }
        return List.copyOf(normalized);
    }

    private void validateLength(String value, int maximum, String field) {
        if (value != null && value.length() > maximum) {
            throw new InvalidWorkspaceSettingsException(field + " must contain " + maximum + " characters or fewer");
        }
    }

    private void validateLogoPath(String value) {
        if (value == null) return;
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("\\")
                || value.chars().anyMatch(Character::isISOControl) || ENCODED_TRAVERSAL.matcher(value).find()) {
            throw new InvalidWorkspaceSettingsException("Public logo path must be a safe same-origin path");
        }
        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getRawPath() == null
                    || Arrays.stream(uri.getRawPath().split("/", -1))
                    .anyMatch(segment -> segment.equals(".") || segment.equals(".."))) {
                throw new InvalidWorkspaceSettingsException("Public logo path must be a safe same-origin path");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidWorkspaceSettingsException("Public logo path must be a safe same-origin path");
        }
    }

    private void validateTimeZone(String value) {
        if (value.length() > 64) throw new InvalidWorkspaceSettingsException("Time zone is invalid");
        try {
            ZoneId.of(value);
        } catch (DateTimeException ex) {
            throw new InvalidWorkspaceSettingsException("Time zone is invalid");
        }
    }

    private void validateCurrency(String value) {
        if (!SupportedCurrency.contains(value)) {
            throw new InvalidWorkspaceSettingsException("Currency is invalid");
        }
    }

    private void validatePrivacy(String url, String notice, String version) {
        validateLength(notice, 1000, "Privacy notice text");
        validateLength(version, 64, "Privacy notice version");
        if ((url == null) != (notice == null) || (version != null && url == null)) {
            throw new InvalidWorkspaceSettingsException(
                    "Privacy-policy URL and notice text must be supplied together");
        }
        if (url == null) return;
        if (url.length() > 2048) throw new InvalidWorkspaceSettingsException("Privacy-policy URL is invalid");
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                throw new InvalidWorkspaceSettingsException("Privacy-policy URL must be an absolute HTTPS URL");
            }
        } catch (IllegalArgumentException ex) {
            throw new InvalidWorkspaceSettingsException("Privacy-policy URL must be an absolute HTTPS URL");
        }
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
