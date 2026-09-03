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
    MockMvcBuilderCustomizer authenticatedMockMvcDefaults() {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "test-admin@example.invalid", "Test Administrator", UserRole.ADMIN,
                WorkspaceTestFixtures.activeWorkspaceA().getId(), "{test}password", true);
        return builder -> builder.defaultRequest(get("/")
                .with(user(principal))
                .with(csrf()));
    }
}
