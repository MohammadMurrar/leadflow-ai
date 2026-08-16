package com.mohammadmurrar.leadflow;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Configuration
public class TestMockMvcSecurityConfiguration {
    @Bean
    MockMvcBuilderCustomizer authenticatedMockMvcDefaults() {
        return builder -> builder.defaultRequest(get("/")
                .with(user("test-admin@example.invalid").roles("ADMIN"))
                .with(csrf()));
    }
}
