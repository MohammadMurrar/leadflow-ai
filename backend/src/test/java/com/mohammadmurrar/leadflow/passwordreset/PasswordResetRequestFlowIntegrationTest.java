package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=true",
        "leadflow.password-reset.active-key-version=v1"
})
@AutoConfigureMockMvc
class PasswordResetRequestFlowIntegrationTest {
    @DynamicPropertySource
    static void resetKey(DynamicPropertyRegistry registry) {
        registry.add("leadflow.password-reset.hmac-keys", () -> "v1=" + "A".repeat(43));
    }
    @Autowired PasswordResetRequestService service;
    @Autowired PasswordResetRequestRepository resets;
    @Autowired EmailOutboxRepository outboxes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    @MockitoSpyBean EmailOutboxService outboxService;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach
    void clear() {
        outboxes.deleteAll();
        resets.deleteAll();
        users.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
    }

    @Test
    void concurrentRequestsProduceOneActiveRequestAndOneDurableIntent() throws Exception {
        users.saveAndFlush(User.createAdministrator(
                workspace, "admin@example.invalid", "Administrator", "hash"));
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<PasswordResetRequestService.Result> first = executor.submit(() -> {
                start.await();
                return service.request("admin@example.invalid", now);
            });
            Future<PasswordResetRequestService.Result> second = executor.submit(() -> {
                start.await();
                return service.request(" ADMIN@example.invalid ", now);
            });
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(PasswordResetRequestService.Result.CREATED,
                            PasswordResetRequestService.Result.SUPPRESSED);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(resets.count()).isOne();
        assertThat(resets.findAll()).allMatch(request -> request.getActiveSlot() != null);
        assertThat(outboxes.count()).isOne();
        EmailOutbox email = outboxes.findAll().getFirst();
        assertThat(email.getTemplateType()).isEqualTo(EmailTemplateType.PASSWORD_RESET);
        assertThat(email.getLead()).isNull();
        assertThat(email.getDeduplicationKey()).matches("password-reset:[0-9a-f]{64}")
                .doesNotContain("admin", "example", "@");
    }

    @Test
    void enqueueFailureRollsBackFlushedSupersessionAndReplacement() {
        User user = users.saveAndFlush(User.createAdministrator(workspace,
                "rollback@example.invalid", "Administrator", "hash"));
        Instant created = Instant.parse("2030-01-01T00:00:00Z");
        byte[] hash = bytes(1);
        byte[] nonce = bytes(2);
        PasswordResetRequest original = resets.saveAndFlush(PasswordResetRequest.createActive(
                UUID.randomUUID(), user, hash, nonce, "v1", created, created.plusSeconds(1800)));
        long originalVersion = original.getVersion();
        doThrow(new EmailOutboxService.EmailOutboxPersistenceException())
                .when(outboxService).enqueuePasswordReset(anyString(),
                        any(PasswordResetRequest.class), anyString(), any(Instant.class));

        assertThatThrownBy(() -> service.request(
                "rollback@example.invalid", created.plusSeconds(301)))
                .isInstanceOf(EmailOutboxService.EmailOutboxPersistenceException.class);

        entityManager.clear();
        PasswordResetRequest reloaded = resets.findById(original.getId()).orElseThrow();
        assertThat(reloaded.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(reloaded.getSupersededAt()).isNull();
        assertThat(reloaded.getDeliveryNonce()).isEqualTo(nonce);
        assertThat(reloaded.getDeliveryKeyVersion()).isEqualTo("v1");
        assertThat(reloaded.getVersion()).isEqualTo(originalVersion);
        assertThat(resets.count()).isOne();
        assertThat(outboxes.count()).isZero();
    }

    @Test
    void enqueueFailureThroughHttpIsGenericAndRollsBackInsteadOfReturningNoContent()
            throws Exception {
        users.saveAndFlush(User.createAdministrator(workspace,
                "http-rollback@example.invalid", "Administrator", "hash"));
        doThrow(new EmailOutboxService.EmailOutboxPersistenceException())
                .when(outboxService).enqueuePasswordReset(anyString(),
                        any(PasswordResetRequest.class), anyString(), any(Instant.class));

        mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"http-rollback@example.invalid\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control",
                        org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.message")
                        .value("The password reset could not be processed"))
                .andExpect(jsonPath("$.details").isEmpty())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("EmailOutboxPersistenceException"))));
        assertThat(resets.count()).isZero();
        assertThat(outboxes.count()).isZero();
    }

    private byte[] bytes(int marker) {
        byte[] value = new byte[32];
        java.util.Arrays.fill(value, (byte) marker);
        return value;
    }
}
