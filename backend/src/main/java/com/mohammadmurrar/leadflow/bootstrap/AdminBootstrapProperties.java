package com.mohammadmurrar.leadflow.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("leadflow.bootstrap")
public record AdminBootstrapProperties(
        boolean enabled,
        String email,
        String displayName,
        String password
) {}
