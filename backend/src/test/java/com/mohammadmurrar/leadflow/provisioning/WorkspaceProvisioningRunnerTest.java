package com.mohammadmurrar.leadflow.provisioning;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WorkspaceProvisioningRunnerTest {
    @Test
    void refusesWebModeBeforeInvokingProvisioningAndClosesContext() {
        WorkspaceProvisioningService provisioning = mock(WorkspaceProvisioningService.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        var environment = new MockEnvironment().withProperty("spring.main.web-application-type", "servlet");
        var runner = new WorkspaceProvisioningRunner(provisioning, environment, context);

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Workspace provisioning failed");
        verifyNoInteractions(provisioning);
        verify(context).close();
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void dryRunInvokesOnlyPreviewAndNeverPrintsSecret(CapturedOutput output) {
        WorkspaceProvisioningService provisioning = mock(WorkspaceProvisioningService.class);
        when(provisioning.preview(any())).thenReturn(new WorkspaceProvisioningService.Preview(true, true, 0, 0));
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        var environment = safeEnvironment().withProperty("PROVISIONING_DRY_RUN", "true");
        var runner = new WorkspaceProvisioningRunner(provisioning, environment, context);

        runner.run(mock(ApplicationArguments.class));

        verify(provisioning).preview(any());
        verify(provisioning, never()).provision(any());
        verify(context).close();
        org.assertj.core.api.Assertions.assertThat(output.getAll())
                .contains("Workspace provisioning preview passed; no data was written")
                .doesNotContain("Correct-Horse", "admin@example.invalid");
    }

    private MockEnvironment safeEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("provisioning");
        return environment
                .withProperty("spring.main.web-application-type", "none")
                .withProperty("leadflow.bootstrap.enabled", "false")
                .withProperty("leadflow.email-delivery.enabled", "false")
                .withProperty("leadflow.qualification-reliability.dispatcher-enabled", "false")
                .withProperty("leadflow.qualification-reliability.retry-enabled", "false")
                .withProperty("leadflow.qualification-reliability.legacy-callback-enabled", "false")
                .withProperty("PROVISIONING_WORKSPACE_NAME", "Client")
                .withProperty("PROVISIONING_PUBLIC_SLUG", "client")
                .withProperty("PROVISIONING_ADMIN_NAME", "Admin")
                .withProperty("PROVISIONING_ADMIN_EMAIL", "admin@example.invalid")
                .withProperty("PROVISIONING_ADMIN_PASSWORD", "Correct-Horse-42!");
    }
}
