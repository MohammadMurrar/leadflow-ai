package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "management.endpoints.web.exposure.include=health",
        "management.endpoint.health.probes.enabled=true",
        "management.endpoint.health.group.readiness.include=readinessState,db",
        "management.endpoint.health.show-details=never"
})
@AutoConfigureMockMvc
class AuthenticationSecurityApiTest {
    private static final String EMAIL = "step20-admin@example.invalid";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired FindByIndexNameSessionRepository<? extends Session> sessions;

    @BeforeEach
    void createAdministrator() {
        if (users.findByNormalizedEmail(User.normalizeEmail(EMAIL)).isEmpty()) {
            users.save(User.createAdministrator(EMAIL, "Step 20 Administrator",
                    passwordEncoder.encode(PASSWORD)));
        }
    }

    @Test
    void csrfEndpointIsPublicAndAdministrativeApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/csrf").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(get("/api/v1/dashboard/stats").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void onlyExactPrivateHealthEndpointsArePubliclyAuthorizedBySpringSecurity() throws Exception {
        mvc.perform(get("/actuator/health/liveness").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health/readiness").with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mvc.perform(get("/actuator/health").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/info").with(anonymous()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard/stats").with(anonymous()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginIsGenericRotatesSessionAndStoresOnlySanitizedPrincipal() throws Exception {
        MockHttpSession initial = new MockHttpSession();
        String initialId = initial.getId();
        String body = "{\"email\":\"  STEP20-ADMIN@EXAMPLE.INVALID  \","
                + "\"password\":\"" + PASSWORD + "\"}";

        var loginResult = mvc.perform(post("/api/v1/auth/login")
                        .with(anonymous()).with(csrf()).session(initial)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(cookie().exists("LEADFLOW_SESSION"))
                .andReturn();
        assertThat(loginResult.getResponse().getCookie("LEADFLOW_SESSION").getValue())
                .isNotEqualTo(initialId);
        var storedSessions = sessions.findByPrincipalName(EMAIL);
        assertThat(storedSessions).isNotEmpty();
        SecurityContext context = storedSessions.values().stream()
                .max(java.util.Comparator.comparing(Session::getLastAccessedTime))
                .orElseThrow()
                .getAttribute("SPRING_SECURITY_CONTEXT");
        Authentication authentication = context.getAuthentication();
        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedPrincipal.class);
        assertThat(((AuthenticatedPrincipal) authentication.getPrincipal()).getPassword()).isNull();

        assertThat(authentication.getName()).isEqualTo(EMAIL);
    }

    @Test
    void invalidLoginResponsesAreGeneric() throws Exception {
        mvc.perform(post("/api/v1/auth/login").with(anonymous()).with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"unknown@example.invalid\",\"password\":\"wrong password value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));

        mvc.perform(post("/api/v1/auth/login").with(anonymous()).with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"too-short\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void mutationsRequireCsrfAndLogoutInvalidatesSession() throws Exception {
        mvc.perform(patch("/api/v1/notifications/read-all").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access is denied"));

        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/api/v1/auth/logout").with(anonymous()).with(csrf()).session(session))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("LEADFLOW_SESSION", 0));

        mvc.perform(get("/api/v1/auth/me").with(anonymous()).session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void browserSessionCannotReplaceAutomationKeyAndKeyCannotAccessAdminApi() throws Exception {
        MockHttpSession session = authenticatedSession();
        mvc.perform(post("/api/v1/automation/leads/11111111-1111-1111-1111-111111111111/qualification")
                        .with(anonymous()).session(session).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/automation/leads/11111111-1111-1111-1111-111111111111/qualification")
                        .with(anonymous()).header("X-Automation-Key", "invalid-key")
                        .contentType("application/json")
                        .content("{\"score\":80,\"priority\":\"HIGH\",\"category\":\"qualified\","
                                + "\"summary\":\"Safe summary\",\"recommendedReply\":\"Safe reply\"}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/dashboard/stats").with(anonymous())
                        .header("X-Automation-Key", "test-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentSessionLimitExpiresOlderSessionsAcrossJdbcRegistry() throws Exception {
        MockCookie oldest = loginCookie();
        loginCookie();
        loginCookie();
        loginCookie();

        mvc.perform(get("/api/v1/auth/me").with(anonymous()).cookie(oldest))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    private MockHttpSession authenticatedSession() throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/login")
                        .with(anonymous()).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private MockCookie loginCookie() throws Exception {
        return (MockCookie) mvc.perform(post("/api/v1/auth/login")
                        .with(anonymous()).with(csrf()).contentType("application/json")
                        .content("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("LEADFLOW_SESSION");
    }
}
