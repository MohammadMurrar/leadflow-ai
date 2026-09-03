package com.mohammadmurrar.leadflow.settings;

import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import com.mohammadmurrar.leadflow.workspace.WorkspaceStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WorkspaceSettingsInitializationIntegrationTest {
    @Autowired WorkspaceSettingsService service;
    @Autowired WorkspaceSettingsRepository settings;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspaceA;
    private Workspace workspaceB;

    @BeforeEach
    void setUp() {
        settings.deleteAll();
        workspaceA = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "settings-a-" + UUID.randomUUID(), "Settings A", WorkspaceStatus.ACTIVE));
        workspaceB = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "settings-b-" + UUID.randomUUID(), "Settings B", WorkspaceStatus.ACTIVE));
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        settings.deleteAll();
        workspaces.deleteById(workspaceA.getId());
        workspaces.deleteById(workspaceB.getId());
    }

    @Test
    void initializesAndReloadsEachWorkspaceIndependently() {
        authenticate(workspaceA);
        service.findWorkspace();
        service.findWorkspace();
        authenticate(workspaceB);
        service.findWorkspace();

        assertThat(settings.findByWorkspaceId(workspaceA.getId())).isPresent();
        assertThat(settings.findByWorkspaceId(workspaceB.getId())).isPresent();
        assertThat(settings.count()).isEqualTo(2);
    }

    @Test
    void concurrentInitializationCreatesOneRowForEachWorkspace() throws Exception {
        runConcurrently(workspaceA, workspaceA, workspaceB, workspaceB);

        assertThat(settings.findByWorkspaceId(workspaceA.getId())).isPresent();
        assertThat(settings.findByWorkspaceId(workspaceB.getId())).isPresent();
        assertThat(settings.count()).isEqualTo(2);
    }

    @Test
    void inactiveAndUnknownWorkspaceContextsFailClosed() {
        Workspace suspended = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "settings-suspended-" + UUID.randomUUID(), "Suspended", WorkspaceStatus.SUSPENDED));
        try {
            authenticate(suspended);
            assertThatThrownBy(service::findWorkspace)
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            authenticate(suspended.getId(), "unknown@example.invalid");
            workspaces.deleteById(suspended.getId());
            assertThatThrownBy(service::findWorkspace)
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThat(settings.count()).isZero();
        } finally {
            if (workspaces.existsById(suspended.getId())) workspaces.deleteById(suspended.getId());
        }
    }

    private void runConcurrently(Workspace... requestedWorkspaces) throws Exception {
        var ready = new CountDownLatch(requestedWorkspaces.length);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(requestedWorkspaces.length)) {
            var futures = java.util.Arrays.stream(requestedWorkspaces).map(workspace ->
                    executor.submit(() -> {
                        authenticate(workspace);
                        ready.countDown();
                        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
                        try {
                            service.findWorkspace();
                        } finally {
                            SecurityContextHolder.clearContext();
                        }
                        return null;
                    })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
    }

    private void authenticate(Workspace workspace) {
        authenticate(workspace.getId(), "settings-admin@example.invalid");
    }

    private void authenticate(UUID workspaceId, String email) {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(), email,
                "Settings Administrator", UserRole.ADMIN, workspaceId, null, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }
}
