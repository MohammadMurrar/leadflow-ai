package com.mohammadmurrar.leadflow.CorsConfig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${leadflow.frontend-origin:http://localhost:5173}") String frontendOrigin) {
        validateOrigin(frontendOrigin);
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "X-Requested-With"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void validateOrigin(String origin) {
        try {
            if (origin == null || origin.isBlank() || !origin.equals(origin.trim())) throw invalidOrigin();
            URI uri = new URI(origin);
            boolean loopbackHttp = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost()));
            if (!("https".equalsIgnoreCase(uri.getScheme()) || loopbackHttp)
                    || uri.getHost() == null || uri.getUserInfo() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw invalidOrigin();
            }
        } catch (URISyntaxException exception) {
            throw invalidOrigin();
        }
    }

    private static IllegalStateException invalidOrigin() {
        return new IllegalStateException("Frontend origin configuration is invalid");
    }
}
