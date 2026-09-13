package com.mohammadmurrar.leadflow;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures;
import com.mohammadmurrar.leadflow.user.UserRole;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Configuration
public class TestMockMvcSecurityConfiguration {
    @Bean
    MockMvcBuilderCustomizer authenticatedMockMvcDefaults(
            com.mohammadmurrar.leadflow.user.UserRepository users,
            com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces) {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "test-admin@example.invalid", "Test Administrator", UserRole.ADMIN,
                WorkspaceTestFixtures.activeWorkspaceA().getId(), "{test}password", true);
        return builder -> builder.defaultRequest(get("/")
                .with(request -> {
                    if (org.mockito.Mockito.mockingDetails(users).isMock()) {
                        return user(principal).postProcessRequest(request);
                    }
                    var workspace = workspaces.findById(WorkspaceTestFixtures.activeWorkspaceA().getId())
                            .orElseGet(() -> workspaces.saveAndFlush(WorkspaceTestFixtures.activeWorkspaceA()));
                    var identity = users.findByNormalizedEmail(principal.email()).orElseGet(() ->
                            users.saveAndFlush(com.mohammadmurrar.leadflow.user.User.createAdministrator(
                                    workspace, principal.email(), principal.displayName(), UUID.randomUUID().toString())));
                    var persisted = new AuthenticatedPrincipal(identity.getId(), identity.getNormalizedEmail(),
                            identity.getDisplayName(), identity.getRole(), workspace.getId(), null, identity.isEnabled());
                    return user(persisted).postProcessRequest(request);
                })
                .with(csrf()));
    }
}
