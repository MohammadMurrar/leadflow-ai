package com.mohammadmurrar.leadflow.bootstrap;

import com.mohammadmurrar.leadflow.settings.WorkspaceSettings;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminBootstrapRunnerTest {
    @Test
    void createsAdministratorOnlyInTheLockedActiveLegacyWorkspace() {
        Fixture fixture = fixture(WorkspaceStatus.ACTIVE);

        fixture.runner.run(mock(org.springframework.boot.ApplicationArguments.class));

        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(fixture.users).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getWorkspace().getId()).isEqualTo(fixture.workspace.getId());
        verify(fixture.context).close();
    }

    @Test
    void refusesBootstrapWhenTheLockedLegacyWorkspaceIsNotActive() {
        Fixture fixture = fixture(WorkspaceStatus.SUSPENDED);

        assertThatThrownBy(() -> fixture.runner.run(mock(org.springframework.boot.ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Administrator bootstrap workspace is unavailable");

        verify(fixture.users, never()).saveAndFlush(any());
        verify(fixture.context, never()).close();
    }

    private Fixture fixture(WorkspaceStatus status) {
        UserRepository users = mock(UserRepository.class);
        WorkspaceSettingsRepository settings = mock(WorkspaceSettingsRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Environment environment = mock(Environment.class);
        ConfigurableApplicationContext context = mock(ConfigurableApplicationContext.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        when(environment.getProperty("spring.main.web-application-type", "")).thenReturn("none");
        when(environment.getProperty("leadflow.qualification-reliability.dispatcher-enabled",
                Boolean.class, false)).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Byte.class))).thenReturn((byte) 1);
        when(users.count()).thenReturn(0L);
        when(encoder.encode(anyString())).thenReturn("{test}encoded");
        Workspace workspace = Workspace.create(UUID.randomUUID(), "leadflow-ai", "LeadFlow AI", status);
        when(settings.findBySingletonKey((byte) 1))
                .thenReturn(Optional.of(WorkspaceSettings.createNeutral(workspace, (byte) 1)));
        var properties = new AdminBootstrapProperties(true, "admin@example.invalid",
                "Administrator", "Synthetic-password-42!");
        return new Fixture(new AdminBootstrapRunner(properties, users, settings, encoder, jdbc,
                environment, context, transactions), users, context, workspace);
    }

    private record Fixture(AdminBootstrapRunner runner, UserRepository users,
                           ConfigurableApplicationContext context, Workspace workspace) {}
}
