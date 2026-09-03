package com.mohammadmurrar.leadflow.passwordreset;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.workspace.Workspace;
import com.mohammadmurrar.leadflow.workspace.WorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.RollbackException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PasswordResetConcurrencyTest {
    @Autowired PasswordResetRequestRepository resets;
    @Autowired UserRepository users;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired TransactionTemplate transactions;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspace;

    @BeforeEach
    void clear() {
        resets.deleteAll();
        users.deleteAll();
        workspace = com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA();
        if (!workspaces.existsById(workspace.getId())) workspace = workspaces.saveAndFlush(workspace);
        else workspace = workspaces.findById(workspace.getId()).orElseThrow();
    }

    @Test
    void staleIndependentPersistenceContextCannotOverwriteTerminalState() {
        PasswordResetRequest saved = savedRequest("optimistic@example.invalid", 1);
        EntityManager first = entityManagers.createEntityManager();
        EntityManager stale = entityManagers.createEntityManager();
        try {
            first.getTransaction().begin();
            stale.getTransaction().begin();
            PasswordResetRequest firstCopy = first.find(PasswordResetRequest.class, saved.getId());
            PasswordResetRequest staleCopy = stale.find(PasswordResetRequest.class, saved.getId());

            firstCopy.consume(Instant.now());
            first.getTransaction().commit();

            staleCopy.supersede(Instant.now());
            assertThatThrownBy(stale.getTransaction()::commit)
                    .isInstanceOfAny(RollbackException.class,
                            ObjectOptimisticLockingFailureException.class);
        } finally {
            if (first.getTransaction().isActive()) first.getTransaction().rollback();
            if (stale.getTransaction().isActive()) stale.getTransaction().rollback();
            first.close();
            stale.close();
        }

        PasswordResetRequest persisted = resets.findById(saved.getId()).orElseThrow();
        assertThat(persisted.getConsumedAt()).isNotNull();
        assertThat(persisted.getSupersededAt()).isNull();
        assertThat(persisted.getActiveSlot()).isNull();
        assertThat(persisted.getVersion()).isEqualTo(1);
    }

    @Test
    void competingPessimisticRequestLockWaitsForOwnerTransaction() throws Exception {
        PasswordResetRequest saved = savedRequest("pessimistic@example.invalid", 2);
        CountDownLatch ownerLocked = new CountDownLatch(1);
        CountDownLatch contenderStarted = new CountDownLatch(1);
        CountDownLatch contenderCompleted = new CountDownLatch(1);
        CountDownLatch releaseOwner = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> owner = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                resets.findByIdForUpdate(saved.getId()).orElseThrow();
                ownerLocked.countDown();
                await(releaseOwner);
            }));
            assertThat(ownerLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> contender = executor.submit(() -> transactions.executeWithoutResult(ignored -> {
                contenderStarted.countDown();
                resets.findByIdForUpdate(saved.getId()).orElseThrow();
                contenderCompleted.countDown();
            }));
            assertThat(contenderStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(contenderCompleted.await(250, TimeUnit.MILLISECONDS)).isFalse();

            releaseOwner.countDown();
            owner.get(5, TimeUnit.SECONDS);
            contender.get(5, TimeUnit.SECONDS);
            assertThat(contenderCompleted.getCount()).isZero();
        } finally {
            releaseOwner.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private PasswordResetRequest savedRequest(String email, int marker) {
        User user = users.saveAndFlush(
                User.createAdministrator(workspace, email, "Reset Admin", "{test}encoded"));
        Instant created = Instant.now();
        return resets.saveAndFlush(PasswordResetRequest.createActive(
                UUID.randomUUID(), user, bytes(marker), bytes(marker + 20),
                "v1", created, created.plusSeconds(1800)));
    }

    private static byte[] bytes(int marker) {
        byte[] value = new byte[32];
        Arrays.fill(value, (byte) marker);
        return value;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out awaiting test latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while awaiting test latch", exception);
        }
    }
}
