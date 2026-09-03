package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Service
public class InquiryEmailRecipientResolver {
    private final WorkspaceSettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;

    public InquiryEmailRecipientResolver(WorkspaceSettingsRepository settingsRepository,
            UserRepository userRepository, WorkspaceRepository workspaceRepository) {
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional(readOnly = true)
    public List<String> resolveNewInquiryRecipients(UUID workspaceId) {
        UUID activeWorkspaceId = requireActiveWorkspace(workspaceId);
        List<String> explicit = resolveExplicitRecipientsForActiveWorkspace(activeWorkspaceId);
        if (!explicit.isEmpty()) return explicit;

        return normalize(userRepository
                .findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                        activeWorkspaceId, UserRole.ADMIN).stream()
                .map(User::getNormalizedEmail)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<String> resolveExplicitRecipients(UUID workspaceId) {
        return resolveExplicitRecipientsForActiveWorkspace(requireActiveWorkspace(workspaceId));
    }

    private List<String> resolveExplicitRecipientsForActiveWorkspace(UUID workspaceId) {
        return normalize(settingsRepository.findByWorkspaceId(workspaceId)
                .map(settings -> settings.getNotificationRecipients())
                .orElseGet(List::of));
    }

    private static UUID requireWorkspaceId(UUID workspaceId) {
        if (workspaceId == null) throw new InvalidRecipientWorkspaceException();
        return workspaceId;
    }

    private UUID requireActiveWorkspace(UUID workspaceId) {
        UUID required = requireWorkspaceId(workspaceId);
        return workspaceRepository.findByIdAndStatus(required, WorkspaceStatus.ACTIVE)
                .map(workspace -> workspace.getId())
                .orElseThrow(InvalidRecipientWorkspaceException::new);
    }

    private static List<String> normalize(List<String> candidates) {
        TreeSet<String> recipients = new TreeSet<>();
        candidates.stream().map(InquiryEmailRecipientResolver::normalizeValid)
                .flatMap(Optional::stream)
                .forEach(recipients::add);
        return List.copyOf(recipients);
    }

    private static Optional<String> normalizeValid(String candidate) {
        if (candidate == null || candidate.isBlank()) return Optional.empty();
        String normalized = candidate.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0) return Optional.empty();
        try {
            InternetAddress address = new InternetAddress(normalized, true);
            address.validate();
            return address.getAddress().equals(normalized) ? Optional.of(normalized) : Optional.empty();
        } catch (AddressException ex) {
            return Optional.empty();
        }
    }

    public static final class InvalidRecipientWorkspaceException extends RuntimeException {
        public InvalidRecipientWorkspaceException() {
            super("Email recipient workspace is invalid");
        }
    }
}
