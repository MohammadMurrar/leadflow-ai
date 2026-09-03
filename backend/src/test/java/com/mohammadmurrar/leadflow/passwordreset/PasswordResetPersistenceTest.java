package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PasswordResetPersistenceTest {
    @Autowired PasswordResetRequestRepository resets;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired com.mohammadmurrar.leadflow.workspace.WorkspaceRepository workspaces;
    private com.mohammadmurrar.leadflow.workspace.Workspace workspace;

    @BeforeEach
    void clear() {
        resets.deleteAll();
        users.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("workspace-a",
                        com.mohammadmurrar.leadflow.workspace.WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(
                        com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA()));
    }

    @Test
    void persistsActiveRowAndSupportsCandidateAndPessimisticReloads() {
        User user = user("persistence@example.invalid");
        PasswordResetRequest saved = resets.saveAndFlush(request(user, 1, Instant.now()));

        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getActiveSlot()).isEqualTo((byte) 1);
        assertThat(saved.getTokenHash()).hasSize(32);
        assertThat(saved.getDeliveryNonce()).hasSize(32);
        assertThat(resets.findByTokenHash(bytes(1))).map(PasswordResetRequest::getId)
                .contains(saved.getId());
        transactions.executeWithoutResult(ignored -> {
            assertThat(users.findByIdForUpdate(user.getId())).map(User::getId)
                    .contains(user.getId());
            assertThat(resets.findActiveByUserIdForUpdate(user.getId())).map(PasswordResetRequest::getId)
                    .contains(saved.getId());
            assertThat(resets.findByIdForUpdate(saved.getId())).map(PasswordResetRequest::getId)
                    .contains(saved.getId());
        });
    }

    @Test
    void uniqueActiveSlotAndTokenHashAreEnforcedWhileHistoryIsAllowed() {
        User user = user("uniqueness@example.invalid");
        PasswordResetRequest first = resets.saveAndFlush(request(user, 1, Instant.now()));
        first.supersede(Instant.now());
        resets.saveAndFlush(first);
        PasswordResetRequest second = resets.saveAndFlush(request(user, 2, Instant.now().plusSeconds(1)));
        second.consume(Instant.now().plusSeconds(2));
        resets.saveAndFlush(second);
        resets.saveAndFlush(request(user, 3, Instant.now().plusSeconds(3)));
        assertThat(resets.count()).isEqualTo(3);

        assertThatThrownBy(() -> resets.saveAndFlush(request(user, 4, Instant.now().plusSeconds(4))))
                .isInstanceOf(DataIntegrityViolationException.class);
        resets.deleteAll();
        PasswordResetRequest duplicateHash = request(user, 7, Instant.now());
        resets.saveAndFlush(duplicateHash);
        assertThatThrownBy(() -> resets.saveAndFlush(request(user, 7, Instant.now().plusSeconds(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queriesExpiredAndTerminalRowsAndUserDeleteCascades() {
        User user = user("cleanup@example.invalid");
        Instant now = Instant.now();
        PasswordResetRequest expired = resets.saveAndFlush(request(user, 1, now.minusSeconds(3600)));
        assertThat(resets.findExpiredActive(now, PageRequest.of(0, 10)))
                .extracting(PasswordResetRequest::getId).containsExactly(expired.getId());
        expired.supersede(now.minusSeconds(100));
        resets.saveAndFlush(expired);
        assertThat(resets.findTerminalBefore(now.minusSeconds(50), PageRequest.of(0, 10)))
                .extracting(PasswordResetRequest::getId).containsExactly(expired.getId());

        users.deleteById(user.getId());
        users.flush();
        assertThat(resets.count()).isZero();
    }

    @Test
    void cleanupQueriesUseUuidTieBreakersForIdenticalTimestamps() {
        Instant created = Instant.parse("2030-01-01T00:00:00Z");
        Instant now = created.plusSeconds(3600);
        User firstUser = user("ordering-one@example.invalid");
        User secondUser = user("ordering-two@example.invalid");
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        PasswordResetRequest second = resets.saveAndFlush(
                request(secondId, secondUser, 2, created));
        PasswordResetRequest first = resets.saveAndFlush(
                request(firstId, firstUser, 1, created));

        assertThat(resets.findExpiredActive(now, PageRequest.of(0, 10)))
                .extracting(PasswordResetRequest::getId).containsExactly(firstId, secondId);

        Instant terminalAt = now.minusSeconds(100);
        second.supersede(terminalAt);
        first.supersede(terminalAt);
        resets.saveAllAndFlush(List.of(second, first));
        assertThat(resets.findTerminalBefore(now, PageRequest.of(0, 10)))
                .extracting(PasswordResetRequest::getId).containsExactly(firstId, secondId);
    }

    @Test
    void encodedPasswordMutationAdvancesOptimisticVersionAndTimestamp() {
        User user = user("mutation@example.invalid");
        Instant baseline = Instant.parse("2000-01-01T00:00:00Z");
        jdbc.update("update users set updated_at = ? where id = ?",
                java.sql.Timestamp.from(baseline), user.getId());
        String encoded = passwordEncoder.encode("synthetic-test-password");

        User persistedBaseline = users.findById(user.getId()).orElseThrow();
        long version = persistedBaseline.getVersion();
        assertThat(persistedBaseline.getUpdatedAt()).isEqualTo(baseline);

        transactions.executeWithoutResult(ignored -> {
            User loaded = users.findById(user.getId()).orElseThrow();
            assertThat(loaded.changePasswordHash(encoded)).isTrue();
            users.saveAndFlush(loaded);
        });
        User updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getVersion()).isEqualTo(version + 1);
        assertThat(updated.getUpdatedAt()).isAfter(baseline);
        assertThat(updated.getPasswordHash()).isEqualTo(encoded);
        assertThat(updated).hasToString("User[redacted]");

        User unchanged = transactions.execute(ignored -> {
            User loaded = users.findById(user.getId()).orElseThrow();
            assertThat(loaded.changePasswordHash(encoded)).isFalse();
            return users.saveAndFlush(loaded);
        });
        assertThat(unchanged.getVersion()).isEqualTo(updated.getVersion());
        assertThat(unchanged.getUpdatedAt()).isEqualTo(updated.getUpdatedAt());
    }

    private User user(String email) {
        return users.saveAndFlush(User.createAdministrator(
                workspace, email, "Reset Admin", "{test}encoded"));
    }

    private PasswordResetRequest request(User user, int marker, Instant created) {
        return request(UUID.randomUUID(), user, marker, created);
    }

    private PasswordResetRequest request(UUID id, User user, int marker, Instant created) {
        return PasswordResetRequest.createActive(id, user, bytes(marker), bytes(marker + 20),
                "v1", created, created.plusSeconds(1800));
    }

    private byte[] bytes(int marker) {
        byte[] value = new byte[32];
        Arrays.fill(value, (byte) marker);
        return value;
    }
}
