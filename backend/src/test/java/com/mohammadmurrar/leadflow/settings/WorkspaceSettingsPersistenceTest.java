package com.mohammadmurrar.leadflow.settings;

import com.mohammadmurrar.leadflow.common.ConflictException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
import com.mohammadmurrar.leadflow.workspace.*;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Transactional
class WorkspaceSettingsPersistenceTest {
    @Autowired WorkspaceSettingsRepository repository;
    @Autowired WorkspaceSettingsService service;
    @Autowired EntityManager entityManager;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspace;

    @BeforeEach
    void authenticateWorkspace() {
        workspace = workspaces.saveAndFlush(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA());
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "test-admin@example.invalid", "Test Admin", UserRole.ADMIN,
                workspace.getId(), null, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void databaseEnforcesOneRowPerWorkspaceAndInitializationIsIdempotent() {
        repository.deleteAll();
        service.findWorkspace();
        service.findWorkspace();
        assertThat(repository.count()).isOne();
        var existing = repository.findByWorkspaceId(workspace.getId()).orElseThrow();
        assertThat(existing.getWorkspace().getId()).isEqualTo(workspace.getId());
        assertThat(repository.initializeIfMissing(UUID.randomUUID(), existing.getWorkspace().getId())).isZero();
        assertThat(repository.count()).isOne();
    }

    @Test
    void optimisticVersionAdvancesOnlyForRealUpdates() {
        repository.deleteAll();
        var initial = service.findWorkspace();
        entityManager.createNativeQuery("update workspace_settings set updated_at = :baseline")
                .setParameter("baseline", Instant.now().minusSeconds(2))
                .executeUpdate();
        entityManager.clear();
        initial = service.findWorkspace();
        var noOp = service.update(request(initial.version(), "My Workspace", List.of()));
        assertThat(noOp.version()).isEqualTo(initial.version());
        assertThat(noOp.updatedAt()).isEqualTo(initial.updatedAt());

        var changed = service.update(request(initial.version(), "Changed Workspace",
                List.of("SECOND@example.com", "first@example.com")));
        assertThat(changed.version()).isGreaterThan(initial.version());
        assertThat(changed.updatedAt()).isAfterOrEqualTo(initial.updatedAt());
        assertThat(changed.timeZone()).isEqualTo("UTC");
        assertThat(changed.currency()).isEqualTo("USD");
        assertThat(changed.responseTimeText()).isEqualTo("We usually respond within one business day.");
        assertThat(changed.notificationRecipients())
                .containsExactly("first@example.com", "second@example.com");
        assertThat(repository.findByWorkspaceId(workspace.getId()).orElseThrow().getWorkspace().getId())
                .isEqualTo(workspace.getId());

        var replaced = service.update(request(changed.version(), "Changed Workspace",
                List.of("replacement@example.com")));
        assertThat(replaced.notificationRecipients()).containsExactly("replacement@example.com");
        assertThat(repository.findByWorkspaceId(workspace.getId()).orElseThrow().getNotificationRecipients())
                .containsExactly("replacement@example.com");
    }

    @Test
    void recipientOnlyChangesAreVersionedAndRetainUnchangedChildren() {
        repository.deleteAll();
        var initial = service.findWorkspace();
        entityManager.createNativeQuery("update workspace_settings set updated_at = :baseline")
                .setParameter("baseline", Instant.now().minusSeconds(2))
                .executeUpdate();
        entityManager.clear();
        initial = service.findWorkspace();

        var first = service.update(request(initial.version(), "My Workspace",
                List.of("b@example.com", "a@example.com")));
        assertThat(first.version()).isGreaterThan(initial.version());
        assertThat(first.updatedAt()).isAfter(initial.updatedAt());
        assertThat(first.notificationRecipients()).containsExactly("a@example.com", "b@example.com");
        UUID retainedId = recipientId("a@example.com");

        var noOp = service.update(request(first.version(), "My Workspace",
                List.of("a@example.com", "b@example.com")));
        assertThat(noOp.version()).isEqualTo(first.version());
        assertThat(noOp.updatedAt()).isEqualTo(first.updatedAt());

        var second = service.update(request(first.version(), "My Workspace",
                List.of("c@example.com", "a@example.com")));
        assertThat(second.version()).isGreaterThan(first.version());
        assertThat(second.updatedAt()).isAfter(first.updatedAt());
        assertThat(second.notificationRecipients()).containsExactly("a@example.com", "c@example.com");
        assertThat(recipientId("a@example.com")).isEqualTo(retainedId);

        assertThatThrownBy(() -> service.update(request(first.version(), "My Workspace",
                List.of("stale@example.com"))))
                .isInstanceOf(ConflictException.class);
        assertThat(service.findWorkspace().notificationRecipients())
                .containsExactly("a@example.com", "c@example.com");
    }

    private UUID recipientId(String email) {
        return entityManager.createQuery("""
                        select recipient.id from WorkspaceNotificationRecipient recipient
                        where recipient.normalizedEmail = :email
                        """, UUID.class)
                .setParameter("email", email)
                .getSingleResult();
    }

    private com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest request(
            long version, String name, List<String> recipients) {
        return new com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest(
                version, name, null, null, null, null, null, "UTC", "USD",
                "We usually respond within one business day.", null, null, null, recipients);
    }
}
