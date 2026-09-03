package com.mohammadmurrar.leadflow.provisioning;

import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.service.ServiceOfferingRepository;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:provisioning;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "leadflow.qualification-reliability.legacy-callback-enabled=false",
        "leadflow.email-delivery.enabled=false"
})
class WorkspaceProvisioningIntegrationTest {
    @Autowired WorkspaceProvisioningService provisioning;
    @Autowired WorkspaceRepository workspaces;
    @Autowired UserRepository users;
    @Autowired WorkspaceSettingsRepository settings;
    @MockitoSpyBean ServiceOfferingRepository services;
    @Autowired AuthenticationManager authenticationManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void claimsOnlyTheUntouchedLegacyMigrationSeed() {
        var legacy = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.workspace.Workspace.create(
                java.util.UUID.randomUUID(), "leadflow-ai", "Legacy Seed",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE));
        settings.saveAndFlush(com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                .createNeutral(legacy, (byte) 1));
        var command = command("leadflow-ai", "legacy-owner@example.invalid");

        assertThat(provisioning.preview(command).slugAvailable()).isTrue();
        provisioning.provision(command("leadflow-ai", "legacy-owner@example.invalid"));

        assertThat(workspaces.findByPublicSlug("leadflow-ai").orElseThrow().getId())
                .isEqualTo(legacy.getId());
        assertThat(users.findByNormalizedEmail("legacy-owner@example.invalid").orElseThrow()
                .getWorkspace().getId()).isEqualTo(legacy.getId());
        assertThatThrownBy(() -> provisioning.provision(
                command("leadflow-ai", "another-owner@example.invalid")))
                .isInstanceOf(WorkspaceProvisioningService.DuplicateSlugException.class);
    }

    @Test
    void provisionsIndependentCompleteTenantsAndAuthenticatesFirstAdmin() {
        provisioning.provision(command("owner-client-a", "owner-a@example.invalid"));
        provisioning.provision(command("owner-client-b", "owner-b@example.invalid"));

        var workspaceA = workspaces.findByPublicSlugAndStatus("owner-client-a",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE).orElseThrow();
        var workspaceB = workspaces.findByPublicSlugAndStatus("owner-client-b",
                com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE).orElseThrow();
        var adminA = users.findByNormalizedEmail("owner-a@example.invalid").orElseThrow();
        assertThat(adminA.getWorkspace().getId()).isEqualTo(workspaceA.getId());
        assertThat(adminA.getRole()).isEqualTo(com.mohammadmurrar.leadflow.user.UserRole.ADMIN);
        assertThat(jdbc.queryForObject("""
                select count(*) from workspace_notification_recipients
                where workspace_id = ? and normalized_email = 'notify@example.invalid'
                """, Integer.class, workspaceA.getId())).isEqualTo(1);
        assertThat(services.existsByWorkspaceIdAndNormalizedName(workspaceA.getId(), "shared service")).isTrue();
        assertThat(services.existsByWorkspaceIdAndNormalizedName(workspaceB.getId(), "shared service")).isTrue();

        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        "owner-a@example.invalid", "Correct-Horse-42!"));
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedPrincipal.class);
        assertThat(((AuthenticatedPrincipal) authentication.getPrincipal()).workspaceId())
                .isEqualTo(workspaceA.getId());
    }

    @Test
    void previewAndAllPrevalidatedFailuresWriteNothing() {
        long workspaceCount = workspaces.count();
        long userCount = users.count();
        WorkspaceProvisioningService.Preview preview = provisioning.preview(
                command("preview-client", "preview@example.invalid"));
        assertThat(preview.slugAvailable()).isTrue();
        assertThat(workspaces.count()).isEqualTo(workspaceCount);

        assertThatThrownBy(() -> provisioning.provision(new WorkspaceProvisioningCommand(
                "Weak", "weak-client", null, "Admin", "weak@example.invalid",
                "weak".toCharArray(), List.of(), List.of())))
                .isInstanceOf(com.mohammadmurrar.leadflow.security.PasswordPolicy.InvalidPasswordException.class);
        assertThat(workspaces.count()).isEqualTo(workspaceCount);
        assertThat(users.count()).isEqualTo(userCount);
    }

    @Test
    void midTransactionFailureAndRepeatedSuccessLeaveNoPartialOrChangedTenant() {
        long workspaceCount = workspaces.count();
        doThrow(new IllegalStateException("Synthetic repository failure"))
                .when(services).save(argThat(service -> "Rollback Service".equals(service.getName())));
        assertThatThrownBy(() -> provisioning.provision(new WorkspaceProvisioningCommand(
                "Rollback Client", "rollback-client", null, "Admin", "rollback@example.invalid",
                "Correct-Horse-42!".toCharArray(), List.of(
                new WorkspaceProvisioningCommand.InitialService("Rollback Service", null, true)), List.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Synthetic repository failure");
        reset(services);
        assertThat(workspaces.count()).isEqualTo(workspaceCount);
        assertThat(users.findByNormalizedEmail("rollback@example.invalid")).isEmpty();

        var command = command("repeat-client", "repeat@example.invalid");
        provisioning.provision(command);
        long after = workspaces.count();
        assertThatThrownBy(() -> provisioning.provision(
                command("repeat-client", "repeat-other@example.invalid")))
                .isInstanceOf(WorkspaceProvisioningService.DuplicateSlugException.class);
        assertThat(workspaces.count()).isEqualTo(after);
    }

    @Test
    void concurrentSlugAndEmailAttemptsCreateAtMostOneTenant() throws Exception {
        assertOneSuccess(List.of(
                () -> provisioning.provision(command("race-slug", "race-a@example.invalid")),
                () -> provisioning.provision(command("race-slug", "race-b@example.invalid"))));
        assertThat(workspaces.existsByPublicSlug("race-slug")).isTrue();

        assertOneSuccess(List.of(
                () -> provisioning.provision(command("race-email-a", "race-email@example.invalid")),
                () -> provisioning.provision(command("race-email-b", "race-email@example.invalid"))));
        assertThat(users.findByNormalizedEmail("race-email@example.invalid")).isPresent();
    }

    private void assertOneSuccess(List<ThrowingAction> actions) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            List<Callable<Boolean>> calls = actions.stream().map(action -> (Callable<Boolean>) () -> {
                start.await();
                try { action.run(); return true; }
                catch (RuntimeException expected) { return false; }
            }).toList();
            var futures = calls.stream().map(executor::submit).toList();
            start.countDown();
            assertThat(futures.stream().filter(future -> {
                try { return future.get(); } catch (Exception exception) { throw new RuntimeException(exception); }
            }).count()).isEqualTo(1);
        }
    }

    private WorkspaceProvisioningCommand command(String slug, String email) {
        return new WorkspaceProvisioningCommand("Owner Client", slug, "Public description",
                "Owner Admin", email, "Correct-Horse-42!".toCharArray(),
                List.of(new WorkspaceProvisioningCommand.InitialService("Shared Service", "Description", true)),
                List.of("notify@example.invalid", "NOTIFY@example.invalid"));
    }

    @FunctionalInterface private interface ThrowingAction { void run(); }
}
