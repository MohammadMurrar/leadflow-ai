package com.mohammadmurrar.leadflow.provisioning;

import com.mohammadmurrar.leadflow.security.PasswordPolicy;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WorkspaceProvisioningServiceTest {
    private final WorkspaceProvisioningService service = new WorkspaceProvisioningService(
            mock(WorkspaceRepository.class), mock(UserRepository.class),
            mock(WorkspaceSettingsRepository.class), mock(ServiceOfferingRepository.class),
            mock(PasswordEncoder.class));

    @Test
    void commandAndNestedServiceNeverRenderSensitiveInputs() {
        var command = command("client-a", "admin-a@example.invalid",
                List.of(new WorkspaceProvisioningCommand.InitialService("Secret project", null, true)));

        assertThat(command.toString()).isEqualTo("WorkspaceProvisioningCommand[redacted]")
                .doesNotContain("Correct-Horse", "admin-a", "client-a");
        assertThat(command.initialServices().getFirst().toString()).isEqualTo("InitialService[redacted]");
    }

    @Test
    void canonicalSlugAndPasswordPolicyAreFailClosed() {
        for (String slug : List.of("Client-A", " client-a", "client_a", "client--", "équipe")) {
            assertThatThrownBy(() -> service.preview(command(slug, "admin-a@example.invalid", List.of())))
                    .isInstanceOf(WorkspaceProvisioningService.InvalidProvisioningCommandException.class);
        }
        assertThatThrownBy(() -> service.preview(new WorkspaceProvisioningCommand(
                "Client", "client-a", null, "Admin", "admin-a@example.invalid",
                "weak".toCharArray(), List.of(), List.of())))
                .isInstanceOf(PasswordPolicy.InvalidPasswordException.class);
    }

    @Test
    void duplicateNormalizedInitialServiceNamesFailBeforePersistence() {
        assertThatThrownBy(() -> service.preview(command("client-a", "admin-a@example.invalid", List.of(
                new WorkspaceProvisioningCommand.InitialService("Strategy  Review", null, true),
                new WorkspaceProvisioningCommand.InitialService(" strategy review ", null, false)))))
                .isInstanceOf(WorkspaceProvisioningService.DuplicateInitialServiceException.class);
    }

    static WorkspaceProvisioningCommand command(String slug, String email,
            List<WorkspaceProvisioningCommand.InitialService> services) {
        return new WorkspaceProvisioningCommand("Client Workspace", slug, "Public description",
                "Client Administrator", email, "Correct-Horse-42!".toCharArray(), services,
                List.of("notify@example.invalid", " NOTIFY@example.invalid "));
    }
}
