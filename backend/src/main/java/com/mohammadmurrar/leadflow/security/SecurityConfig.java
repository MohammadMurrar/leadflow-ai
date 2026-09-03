package com.mohammadmurrar.leadflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.web.cors.CorsConfigurationSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        return new DelegatingPasswordEncoder("argon2@SpringSecurity_v5_8",
                Map.of("argon2@SpringSecurity_v5_8", argon2));
    }

    @Bean
    AuthenticationManager authenticationManager(DatabaseUserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    <S extends Session> SessionRegistry sessionRegistry(FindByIndexNameSessionRepository<S> sessions) {
        return new SpringSessionBackedSessionRegistry<>(sessions);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry registry,
            CsrfTokenRepository csrfTokenRepository) {
        ConcurrentSessionControlAuthenticationStrategy concurrent =
                new ConcurrentSessionControlAuthenticationStrategy(registry);
        concurrent.setMaximumSessions(3);
        concurrent.setExceptionIfMaximumExceeded(false);
        return new CompositeSessionAuthenticationStrategy(List.of(
                concurrent,
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(csrfTokenRepository),
                new RegisterSessionAuthenticationStrategy(registry)
        ));
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.secure(secure));
        return repository;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
            CookieCsrfTokenRepository csrf, ObjectMapper objectMapper,
            SessionRegistry sessionRegistry) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        RequestMatcher legacy = paths.matcher(HttpMethod.POST,
                "/api/v1/automation/leads/{leadId}/qualification");
        RequestMatcher start = paths.matcher(HttpMethod.POST,
                "/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/start");
        RequestMatcher success = paths.matcher(HttpMethod.POST,
                "/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/success");
        RequestMatcher failure = paths.matcher(HttpMethod.POST,
                "/api/v1/automation/leads/{leadId}/qualification-attempts/{attemptId}/failure");

        http
                .cors(configuration -> configuration.configurationSource(cors))
                .csrf(configuration -> configuration
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(legacy, start, success, failure))
                .sessionManagement(configuration -> configuration
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .maximumSessions(3)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry)
                        .expiredSessionStrategy(event -> writeError(event.getResponse(), objectMapper,
                                401, "Authentication is required")))
                .securityContext(configuration -> configuration
                        .securityContextRepository(securityContextRepository()))
                .authorizeHttpRequests(authorization -> authorization
                        .dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR).permitAll()
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        .requestMatchers("/api/v1/auth/csrf", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/password-reset/request",
                                "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers("/api/v1/automation/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/inquiry-config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/public/leads").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/public/workspaces/{workspaceSlug}/inquiry-config").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/public/workspaces/{workspaceSlug}/leads").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .exceptionHandling(configuration -> configuration
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, objectMapper, 401, "Authentication is required"))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, objectMapper, 403, "Access is denied")))
                .logout(configuration -> configuration
                        .logoutUrl("/api/v1/auth/logout")
                        .deleteCookies("LEADFLOW_SESSION", "XSRF-TOKEN")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .headers(configuration -> configuration
                        .contentTypeOptions(options -> {})
                        .frameOptions(options -> options.deny())
                        .referrerPolicy(policy -> policy.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(policy -> policy.policy(
                                "camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    private static void writeError(HttpServletResponse response, ObjectMapper mapper,
            int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getOutputStream(), Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "message", message,
                "details", List.of()
        ));
    }
}
