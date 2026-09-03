package com.mohammadmurrar.leadflow.api;

import com.mohammadmurrar.leadflow.email.EmailOutboxRepository;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestRepository;
import com.mohammadmurrar.leadflow.user.*;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.*;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=true",
        "leadflow.password-reset.active-key-version=v1",
        "leadflow.password-reset.hmac-keys=v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
@AutoConfigureMockMvc
class PasswordResetEnumerationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired EmailOutboxRepository outboxes;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspace;

    @BeforeEach
    void clear() {
        outboxes.deleteAll();
        requests.deleteAll();
        users.deleteAll();
        workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        if (!workspaces.existsById(workspace.getId())) workspace = workspaces.saveAndFlush(workspace);
        else workspace = workspaces.findById(workspace.getId()).orElseThrow();
    }

    @Test
    void textualAccountOutcomesHaveIdenticalHttpResponsesAndOnlyEligibleCreatesState()
            throws Exception {
        ResponseShape unknownBeforeCreation = submit("eligible@example.invalid");
        assertThat(requests.count()).isZero();
        assertThat(outboxes.count()).isZero();

        users.saveAndFlush(User.createAdministrator(workspace,
                "eligible@example.invalid", "Admin", "hash"));
        ResponseShape eligible = submit("eligible@example.invalid");
        assertThat(requests.count()).isOne();
        assertThat(outboxes.count()).isOne();

        ResponseShape unknown = submit("unknown@example.invalid");
        ResponseShape cooldown = submit("eligible@example.invalid");

        User disabled = users.saveAndFlush(User.createAdministrator(workspace,
                "disabled@example.invalid", "Disabled", "hash"));
        jdbc.update("update users set enabled = false where id = ?", disabled.getId());
        ResponseShape disabledResponse = submit("disabled@example.invalid");

        List<ResponseShape> responses = new ArrayList<>(List.of(unknownBeforeCreation, eligible,
                unknown, cooldown,
                disabledResponse, submit("not-an-email"), submit("   "),
                submit("x".repeat(255))));
        assertThat(responses).allSatisfy(response -> assertThat(response).isEqualTo(eligible));
        assertThat(requests.count()).isOne();
        assertThat(outboxes.count()).isOne();
    }

    private ResponseShape submit(String email) throws Exception {
        String body = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(Map.of("email", email));
        MvcResult result = mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();
        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        assertThat(response.getHeader("Location")).isNull();
        return new ResponseShape(response.getStatus(), response.getContentAsByteArray(),
                response.getHeader("Cache-Control"), response.getHeader("Location"),
                response.getContentType());
    }

    private record ResponseShape(int status, byte[] body, String cacheControl, String location,
                                 String contentType) {
        @Override public boolean equals(Object other) {
            return other instanceof ResponseShape shape && status == shape.status
                    && Arrays.equals(body, shape.body)
                    && Objects.equals(cacheControl, shape.cacheControl)
                    && Objects.equals(location, shape.location)
                    && Objects.equals(contentType, shape.contentType);
        }
        @Override public int hashCode() {
            return Objects.hash(status, Arrays.hashCode(body), cacheControl, location, contentType);
        }
    }
}
