package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.CorsConfig.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {
    private final CorsConfig config = new CorsConfig();

    @Test
    void acceptsHttpsAndLoopbackDevelopmentOrigins() {
        assertThat(configuration("https://app.example.invalid").getAllowedOrigins())
                .containsExactly("https://app.example.invalid");
        assertThat(configuration("http://localhost:5173").getAllowedOrigins())
                .containsExactly("http://localhost:5173");
    }

    @Test
    void rejectsWildcardUnencryptedRemoteAndNonOriginValues() {
        for (String candidate : new String[]{"*", "http://example.invalid", " https://example.invalid",
                "https://example.invalid/path", "https://example.invalid?next=bad",
                "https://user@example.invalid", "javascript:alert(1)"}) {
            assertThatThrownBy(() -> config.corsConfigurationSource(candidate))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Frontend origin configuration is invalid");
        }
    }

    private CorsConfiguration configuration(String origin) {
        UrlBasedCorsConfigurationSource source = (UrlBasedCorsConfigurationSource)
                config.corsConfigurationSource(origin);
        return source.getCorsConfigurations().get("/api/**");
    }
}
