package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "leadflow.password-reset.enabled=true",
        "leadflow.password-reset.active-key-version=v1",
        "leadflow.password-reset.hmac-keys=v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
})
class PasswordResetConfirmationIntegrationTest {
    @Autowired PasswordResetConfirmationService confirmations;
    @Autowired PasswordResetTokenService tokens;
    @Autowired PasswordResetRequestRepository requests;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @SuppressWarnings("rawtypes")
    @Autowired SessionRepository sessionRepository;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean PasswordResetSessionService sessionInvalidation;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach
    void clear() {
        org.mockito.Mockito.reset(sessionInvalidation);
        jdbc.update("delete from SPRING_SESSION");
        requests.deleteAll();
        users.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
    }

    @Test
    void successUsesProductionEncoderConsumesOnceAndDeletesOnlyTargetSessions() {
        String oldPassword = "Old-password-42!";
        User target = user("target@example.invalid", oldPassword);
        User other = user("other@example.invalid", oldPassword);
        TokenFixture reset = reset(target);
        String targetOne = session(target.getNormalizedEmail());
        String targetTwo = session(target.getNormalizedEmail());
        String otherSession = session(other.getNormalizedEmail());

        confirmations.confirm(reset.token(), "New-password-84!");

        User changed = users.findById(target.getId()).orElseThrow();
        PasswordResetRequest consumed = requests.findById(reset.id()).orElseThrow();
        assertThat(encoder.matches("New-password-84!", changed.getPasswordHash())).isTrue();
        assertThat(changed.getPasswordHash()).doesNotContain("New-password-84!");
        assertThat(consumed.getConsumedAt()).isNotNull();
        assertThat(consumed.getActiveSlot()).isNull();
        assertThat(sessionRepository.findById(targetOne)).isNull();
        assertThat(sessionRepository.findById(targetTwo)).isNull();
        assertThat(sessionRepository.findById(otherSession)).isNotNull();
        assertThatThrownBy(() -> confirmations.confirm(reset.token(), "Another-password-95!"))
                .isExactlyInstanceOf(
                        PasswordResetConfirmationService.InvalidPasswordResetException.class)
                .hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE);
    }

    @Test
    void expiredAndPolicyRejectedRequestsDoNotMutatePersistentState() {
        User target = user("policy@example.invalid", "Old-password-42!");
        TokenFixture active = reset(target);
        String sessionId = session(target.getNormalizedEmail());
        String originalHash = target.getPasswordHash();

        assertThatThrownBy(() -> confirmations.confirm(active.token(), "policy-NewPass42!"))
                .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordException.class);
        assertUnchanged(active.id(), target.getId(), originalHash, sessionId);

        jdbc.update("update password_reset_requests set expires_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), active.id());
        assertThatThrownBy(() -> confirmations.confirm(active.token(), "New-password-84!"))
                .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class);
        assertUnchanged(active.id(), target.getId(), originalHash, sessionId);
    }

    @Test
    void sessionDeletionFailureRollsBackPasswordAndConsumption() {
        User target = user("rollback@example.invalid", "Old-password-42!");
        TokenFixture reset = reset(target);
        String sessionId = session(target.getNormalizedEmail());
        String originalHash = target.getPasswordHash();
        doThrow(new IllegalStateException("synthetic session failure"))
                .when(sessionInvalidation).deleteByPrincipalName(anyString());

        assertThatThrownBy(() -> confirmations.confirm(reset.token(), "New-password-84!"))
                .isInstanceOf(IllegalStateException.class);

        assertUnchanged(reset.id(), target.getId(), originalHash, sessionId);
    }

    @Test
    void persistenceFailureAfterSessionDeletionRollsBackAllThreeStateChanges() {
        User target = user("flush-rollback@example.invalid", "Old-password-42!");
        TokenFixture reset = reset(target);
        String sessionId = session(target.getNormalizedEmail());
        String originalHash = target.getPasswordHash();
        doAnswer(invocation -> {
            int deleted = (int) invocation.callRealMethod();
            jdbc.update("update password_reset_requests set version = version + 1 where id = ?",
                    reset.id());
            return deleted;
        }).when(sessionInvalidation).deleteByPrincipalName(target.getNormalizedEmail());

        assertThatThrownBy(() -> confirmations.confirm(reset.token(), "New-password-84!"))
                .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class)
                .hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE);

        assertUnchanged(reset.id(), target.getId(), originalHash, sessionId);
        assertThat(requests.findById(reset.id()).orElseThrow().getVersion()).isZero();
    }

    @Test
    void malformedStoredPasswordHashUsesGenericFailureAndRollsBackAllState() {
        User target = users.saveAndFlush(User.createAdministrator(workspace,
                "malformed-hash@example.invalid", "Reset Admin", "unsupported-hash"));
        TokenFixture reset = reset(target);
        String sessionId = session(target.getNormalizedEmail());
        String originalHash = target.getPasswordHash();

        assertThatThrownBy(() -> confirmations.confirm(reset.token(), "New-password-84!"))
                .isExactlyInstanceOf(
                        PasswordResetConfirmationService.InvalidPasswordResetException.class)
                .hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE)
                .hasMessageNotContaining(originalHash)
                .hasMessageNotContaining(target.getNormalizedEmail())
                .hasMessageNotContaining(reset.token());

        assertUnchanged(reset.id(), target.getId(), originalHash, sessionId);
    }

    @Test
    void concurrentConfirmationHasExactlyOneSuccessAndOneGenericFailure() throws Exception {
        User target = user("concurrent@example.invalid", "Old-password-42!");
        TokenFixture reset = reset(target);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<String> confirmation = () -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    confirmations.confirm(reset.token(), "New-password-84!");
                    return "success";
                } catch (PasswordResetConfirmationService.InvalidPasswordResetException exception) {
                    assertThat(exception).hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE);
                    return "invalid";
                }
            };
            Future<String> first = executor.submit(confirmation);
            Future<String> second = executor.submit(confirmation);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("success", "invalid");
        } finally {
            executor.shutdownNow();
        }
        assertThat(requests.findById(reset.id()).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(encoder.matches("New-password-84!",
                users.findById(target.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    private User user(String email, String password) {
        return users.saveAndFlush(User.createAdministrator(workspace, email, "Reset Admin",
                encoder.encode(password)));
    }

    private TokenFixture reset(User user) {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        Instant expires = now.plusSeconds(1800);
        UUID id = UUID.randomUUID();
        byte[] nonce = tokens.newDeliveryNonce();
        try (var token = tokens.derive(id, user.getId(), expires, nonce, "v1")) {
            byte[] bytes = token.bytes();
            byte[] hash = tokens.storedHash(bytes);
            try {
                requests.saveAndFlush(PasswordResetRequest.createActive(id, user, hash, nonce,
                        "v1", now, expires));
                return new TokenFixture(id, token.encoded());
            } finally {
                Arrays.fill(bytes, (byte) 0);
                Arrays.fill(hash, (byte) 0);
                Arrays.fill(nonce, (byte) 0);
            }
        }
    }

    private String session(String principal) {
        Session session = sessionRepository.createSession();
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, principal);
        sessionRepository.save(session);
        return session.getId();
    }

    private void assertUnchanged(UUID requestId, UUID userId, String passwordHash, String sessionId) {
        assertThat(users.findById(userId).orElseThrow().getPasswordHash()).isEqualTo(passwordHash);
        PasswordResetRequest request = requests.findById(requestId).orElseThrow();
        assertThat(request.getConsumedAt()).isNull();
        assertThat(request.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(sessionRepository.findById(sessionId)).isNotNull();
    }

    private record TokenFixture(UUID id, String token) {}
}
