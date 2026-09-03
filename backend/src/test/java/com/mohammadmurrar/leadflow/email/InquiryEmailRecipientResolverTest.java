package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InquiryEmailRecipientResolverTest {
    private final Workspace workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
    private final WorkspaceSettingsRepository settings = mock(WorkspaceSettingsRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final InquiryEmailRecipientResolver resolver =
            new InquiryEmailRecipientResolver(settings, users, workspaces);

    @Test
    void fallsBackToEnabledAdministratorWhenExplicitRecipientsAreEmpty() {
        configured(List.of());
        List<User> administrators = List.of(user(" ADMIN@EXAMPLE.INVALID "));
        active();
        when(users.findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                workspace.getId(), UserRole.ADMIN))
                .thenReturn(administrators);

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId()))
                .containsExactly("admin@example.invalid");
    }

    @Test
    void explicitRecipientsTakePrecedenceWithoutAddingAdministratorFallback() {
        configured(List.of("alerts@example.invalid"));
        active();

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId()))
                .containsExactly("alerts@example.invalid");
        verifyNoInteractions(users);
    }

    @Test
    void normalizesDeduplicatesAndOrdersMultipleExplicitRecipients() {
        configured(List.of(" ZETA@EXAMPLE.INVALID ", "alpha@example.invalid",
                "zeta@example.invalid"));
        active();

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId()))
                .containsExactly("alpha@example.invalid", "zeta@example.invalid");
    }

    @Test
    void excludesMalformedCandidatesAndUsesDeterministicAdministratorOrder() {
        configured(List.of("not-an-email"));
        active();
        List<User> administrators = List.of(user("zeta@example.invalid"), user("not-an-email"),
                user(" ALPHA@EXAMPLE.INVALID "), user("alpha@example.invalid"));
        when(users.findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                workspace.getId(), UserRole.ADMIN))
                .thenReturn(administrators);

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId()))
                .containsExactly("alpha@example.invalid", "zeta@example.invalid");
    }

    @Test
    void configuredAdministratorAddressProducesOnlyOneExplicitDeliveryTarget() {
        configured(List.of(" ADMIN@EXAMPLE.INVALID ", "admin@example.invalid"));
        active();

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId()))
                .containsExactly("admin@example.invalid");
        verifyNoInteractions(users);
    }

    @Test
    void noWorkspaceAndNoEligibleAdministratorReturnsEmpty() {
        active();
        when(settings.findByWorkspaceId(workspace.getId())).thenReturn(Optional.empty());
        when(users.findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                workspace.getId(), UserRole.ADMIN))
                .thenReturn(List.of());

        assertThat(resolver.resolveNewInquiryRecipients(workspace.getId())).isEmpty();
    }

    @Test
    void repositoryQueryIsRestrictedToEnabledAdministrators() {
        configured(List.of());
        active();
        when(users.findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                workspace.getId(), UserRole.ADMIN))
                .thenReturn(List.of());

        resolver.resolveNewInquiryRecipients(workspace.getId());

        verify(users).findAllByWorkspaceIdAndRoleAndEnabledTrueOrderByNormalizedEmailAsc(
                workspace.getId(), UserRole.ADMIN);
    }

    @Test
    void suspendedWorkspaceFailsClosedWithoutRecipientOrAdministratorLookup() {
        when(workspaces.findByIdAndStatus(workspace.getId(), WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> resolver.resolveNewInquiryRecipients(workspace.getId())))
                .isInstanceOf(InquiryEmailRecipientResolver.InvalidRecipientWorkspaceException.class)
                .hasMessage("Email recipient workspace is invalid");
        verifyNoInteractions(settings, users);
    }

    private void configured(List<String> recipients) {
        WorkspaceSettings configured = mock(WorkspaceSettings.class);
        when(configured.getNotificationRecipients()).thenReturn(recipients);
        when(settings.findByWorkspaceId(workspace.getId())).thenReturn(Optional.of(configured));
    }

    private void active() {
        when(workspaces.findByIdAndStatus(workspace.getId(), WorkspaceStatus.ACTIVE))
                .thenReturn(Optional.of(workspace));
    }

    private User user(String normalizedEmail) {
        User user = mock(User.class);
        when(user.getNormalizedEmail()).thenReturn(normalizedEmail);
        return user;
    }
}
