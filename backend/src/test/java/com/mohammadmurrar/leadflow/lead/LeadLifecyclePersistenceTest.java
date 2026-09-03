package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.lead.api.LeadResponse;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.*;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class LeadLifecyclePersistenceTest {
    @Autowired LeadRepository repository;
    @Autowired LeadService service;
    @Autowired EntityManager entityManager;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspace;

    @BeforeEach
    void authenticateWorkspace() {
        workspace = workspaces.saveAndFlush(com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA());
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "lifecycle-admin@example.invalid", "Lifecycle Admin", UserRole.ADMIN,
                workspace.getId(), null, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void realTransitionChangesUpdatedAtAndVersionWhileIdempotentRequestDoesNot() {
        Lead lead = Lead.create(workspace, "Persistence Lead", "persistence-lifecycle@example.com", null,
                "Persistence Co", "Lifecycle testing", null, new BigDecimal("3000.00"),
                LocalDate.parse("2026-09-01"), "A detailed persistence lifecycle request.", "test");
        lead.startQualification();
        lead.applyQualification(89, LeadPriority.HIGH, "Enterprise",
                "Stable qualification summary", "Stable recommended reply");
        lead = repository.saveAndFlush(lead);
        Instant baseline = Instant.now().minusSeconds(2);
        entityManager.createNativeQuery("update leads set updated_at = :baseline where id = :id")
                .setParameter("baseline", baseline)
                .setParameter("id", lead.getId())
                .executeUpdate();
        entityManager.clear();
        lead = repository.findById(lead.getId()).orElseThrow();

        long initialVersion = lead.getVersion();
        Instant createdAt = lead.getCreatedAt();
        Instant initialUpdatedAt = lead.getUpdatedAt();
        LeadResponse transitioned = service.changeStatus(lead.getId(), LeadStatus.CONTACTED, initialVersion);

        assertThat(transitioned.version()).isEqualTo(initialVersion + 1);
        assertThat(transitioned.createdAt()).isEqualTo(createdAt);
        assertThat(transitioned.updatedAt()).isAfter(initialUpdatedAt);
        assertThat(transitioned.qualificationScore()).isEqualTo(89);
        assertThat(transitioned.aiSummary()).isEqualTo("Stable qualification summary");

        LeadResponse repeated = service.changeStatus(lead.getId(), LeadStatus.CONTACTED, transitioned.version());
        assertThat(repeated.version()).isEqualTo(transitioned.version());
        assertThat(repeated.updatedAt()).isEqualTo(transitioned.updatedAt());
        assertThat(repeated.createdAt()).isEqualTo(createdAt);
    }
}
