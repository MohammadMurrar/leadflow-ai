package com.mohammadmurrar.leadflow.provisioning;

import com.mohammadmurrar.leadflow.security.PasswordPolicy;
import com.mohammadmurrar.leadflow.service.ServiceOffering;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class WorkspaceProvisioningService {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final WorkspaceRepository workspaces;
    private final UserRepository users;
    private final WorkspaceSettingsRepository settings;
    private final ServiceOfferingRepository services;
    private final PasswordEncoder passwordEncoder;

    public WorkspaceProvisioningService(WorkspaceRepository workspaces, UserRepository users,
            WorkspaceSettingsRepository settings, ServiceOfferingRepository services,
            PasswordEncoder passwordEncoder) {
        this.workspaces = workspaces;
        this.users = users;
        this.settings = settings;
        this.services = services;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void provision(WorkspaceProvisioningCommand command) {
        char[] password = null;
        String rawPassword = null;
        try {
            Validated input = validate(command);
            if (users.findByNormalizedEmail(input.email()).isPresent()) throw duplicateEmail();
            password = command.administratorPassword();
            rawPassword = new String(password);
            PasswordPolicy.validateNewPassword(rawPassword, input.email());
            Workspace workspace;
            WorkspaceSettings workspaceSettings;
            var existing = workspaces.findByPublicSlugForUpdate(input.slug());
            if (existing.isPresent()) {
                workspace = existing.get();
                workspaceSettings = claimableLegacySettings(workspace);
            } else {
                workspace = workspaces.saveAndFlush(Workspace.create(
                        UUID.randomUUID(), input.slug(), input.workspaceName(), WorkspaceStatus.ACTIVE));
                workspaceSettings = WorkspaceSettings.createNeutral(workspace, (byte) 2);
            }
            users.saveAndFlush(User.createAdministrator(workspace, input.email(),
                    input.adminName(), passwordEncoder.encode(rawPassword)));
            workspaceSettings.update(input.workspaceName(), null, input.description(),
                    input.workspaceName(), input.description(), null, "UTC", "USD",
                    "We usually respond within one business day.", null, null, null,
                    input.recipients());
            settings.saveAndFlush(workspaceSettings);
            for (ValidatedService definition : input.services()) {
                ServiceOffering offering = ServiceOffering.create(workspace,
                        definition.name(), definition.description());
                if (!definition.active()) offering.deactivate();
                services.save(offering);
            }
            services.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProvisioningConflictException();
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (command != null) command.clearPassword();
            rawPassword = null;
        }
    }

    private WorkspaceSettings claimableLegacySettings(Workspace workspace) {
        if (!isClaimableLegacySeed(workspace)) throw duplicateSlug();
        WorkspaceSettings existingSettings = settings.findByWorkspaceId(workspace.getId())
                .orElseThrow(WorkspaceProvisioningService::duplicateSlug);
        return existingSettings;
    }

    private boolean isClaimableLegacySeed(Workspace workspace) {
        if (!"leadflow-ai".equals(workspace.getPublicSlug()) || !workspace.isActive()
                || users.existsByWorkspaceId(workspace.getId())
                || services.existsByWorkspaceId(workspace.getId())) return false;
        return settings.findByWorkspaceId(workspace.getId())
                .map(value -> value.getNotificationRecipients().isEmpty())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Preview preview(WorkspaceProvisioningCommand command) {
        char[] password = null;
        try {
            Validated input = validate(command);
            password = command.administratorPassword();
            PasswordPolicy.validateNewPassword(new String(password), input.email());
            boolean slugAvailable = workspaces.findByPublicSlug(input.slug())
                    .map(this::isClaimableLegacySeed).orElse(true);
            return new Preview(slugAvailable,
                    users.findByNormalizedEmail(input.email()).isEmpty(), input.services().size(),
                    input.recipients().size());
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (command != null) command.clearPassword();
        }
    }

    private static Validated validate(WorkspaceProvisioningCommand command) {
        if (command == null) throw invalid();
        char[] password = command.administratorPassword();
        if (password == null) throw invalid();
        Arrays.fill(password, '\0');
        String slug = command.publicSlug();
        if (slug == null || slug.length() > 63 || !SLUG.matcher(slug).matches()) throw invalid();
        String workspaceName = normalizeRequired(command.workspaceDisplayName(), 120);
        String adminName = normalizeRequired(command.administratorDisplayName(), 120);
        String email;
        try { email = User.normalizeEmail(command.administratorEmail()); }
        catch (IllegalArgumentException exception) { throw invalid(); }
        if (email.length() > 254 || !EMAIL.matcher(email).matches()) throw invalid();
        String description = normalizeOptional(command.publicDescription(), 500);
        LinkedHashSet<String> recipients = new LinkedHashSet<>();
        for (String recipient : command.notificationRecipients()) {
            String normalized;
            try { normalized = User.normalizeEmail(recipient); }
            catch (IllegalArgumentException exception) { throw invalid(); }
            if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) throw invalid();
            recipients.add(normalized);
        }
        List<ValidatedService> serviceDefinitions = new ArrayList<>();
        Set<String> names = new LinkedHashSet<>();
        for (WorkspaceProvisioningCommand.InitialService service : command.initialServices()) {
            if (service == null) throw invalid();
            String key;
            try { key = ServiceOffering.normalizedKey(service.name()); }
            catch (RuntimeException exception) { throw invalid(); }
            if (key.length() > 120 || (service.description() != null
                    && service.description().trim().length() > 1000)) throw invalid();
            if (!names.add(key)) throw new DuplicateInitialServiceException();
            serviceDefinitions.add(new ValidatedService(service.name(), service.description(), service.active()));
        }
        return new Validated(workspaceName, slug, description, adminName, email,
                List.copyOf(recipients), List.copyOf(serviceDefinitions));
    }

    private static String normalizeRequired(String value, int maximum) {
        if (value == null || value.isBlank()) throw invalid();
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > maximum) throw invalid();
        return normalized;
    }

    private static String normalizeOptional(String value, int maximum) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximum) throw invalid();
        return normalized;
    }

    private static InvalidProvisioningCommandException invalid() {
        return new InvalidProvisioningCommandException();
    }
    private static DuplicateSlugException duplicateSlug() { return new DuplicateSlugException(); }
    private static DuplicateAdministratorEmailException duplicateEmail() {
        return new DuplicateAdministratorEmailException();
    }

    private record Validated(String workspaceName, String slug, String description,
            String adminName, String email, List<String> recipients,
            List<ValidatedService> services) {}
    private record ValidatedService(String name, String description, boolean active) {}

    public record Preview(boolean slugAvailable, boolean administratorEmailAvailable,
            int initialServiceCount, int notificationRecipientCount) {}

    public static class InvalidProvisioningCommandException extends RuntimeException {
        public InvalidProvisioningCommandException() { super("Workspace provisioning input is invalid"); }
    }
    public static class DuplicateSlugException extends RuntimeException {
        public DuplicateSlugException() { super("Workspace slug is already in use"); }
    }
    public static class DuplicateAdministratorEmailException extends RuntimeException {
        public DuplicateAdministratorEmailException() { super("Administrator email is already in use"); }
    }
    public static class DuplicateInitialServiceException extends RuntimeException {
        public DuplicateInitialServiceException() { super("Initial service names must be unique"); }
    }
    public static class ProvisioningConflictException extends RuntimeException {
        public ProvisioningConflictException() { super("Workspace provisioning conflicts with existing data"); }
    }
}
