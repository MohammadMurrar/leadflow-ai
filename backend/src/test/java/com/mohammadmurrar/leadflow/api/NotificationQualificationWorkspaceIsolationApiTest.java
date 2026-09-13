package com.mohammadmurrar.leadflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.notification.*;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.security.DatabaseUserDetailsService;
import com.mohammadmurrar.leadflow.user.*;
import com.mohammadmurrar.leadflow.workspace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "leadflow.qualification-reliability.retry-enabled=true")
@AutoConfigureMockMvc
@Transactional
class NotificationQualificationWorkspaceIsolationApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceRepository workspaces;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired DatabaseUserDetailsService userDetails;
    @Autowired LeadRepository leads;
    @Autowired NotificationRepository notifications;
    @Autowired QualificationAttemptRepository attempts;
    @Autowired QualificationDispatchOutboxRepository outboxes;

    private Workspace workspaceA;
    private Workspace workspaceB;
    private Workspace pending;
    private Workspace suspended;
    private AuthenticatedPrincipal adminA;
    private AuthenticatedPrincipal adminB;

    @BeforeEach
    void setUp() {
        workspaceA = workspace("leadflow-ai", WorkspaceStatus.ACTIVE);
        workspaceB = workspace("workspace-b-" + UUID.randomUUID(), WorkspaceStatus.ACTIVE);
        pending = workspace("pending-" + UUID.randomUUID(), WorkspaceStatus.PENDING);
        suspended = workspace("suspended-" + UUID.randomUUID(), WorkspaceStatus.SUSPENDED);
        adminA = administrator(workspaceA, "notification-a-" + UUID.randomUUID() + "@example.invalid");
        adminB = administrator(workspaceB, "notification-b-" + UUID.randomUUID() + "@example.invalid");
    }

    @Test
    void notificationCollectionsCountsAndMutationsRemainWorkspaceIsolated() throws Exception {
        Lead leadA = leads.saveAndFlush(lead(workspaceA, "Notification A", "notification-a@example.invalid"));
        Lead leadB = leads.saveAndFlush(lead(workspaceB, "Notification B", "notification-b@example.invalid"));
        Notification unreadA = notifications.saveAndFlush(notification(leadA, "Unread A"));
        Notification readA = notification(leadA, "Read A");
        readA.markAsRead();
        notifications.saveAndFlush(readA);
        Notification unreadB = notifications.saveAndFlush(notification(leadB, "Unread B"));

        mockMvc.perform(get("/api/v1/notifications").param("size", "1").with(user(adminA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[*].id", not(hasItem(unreadB.getId().toString()))));
        mockMvc.perform(get("/api/v1/notifications").with(user(adminB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(unreadB.getId().toString()));
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", unreadA.getId())
                        .with(user(adminA)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(notifications.findById(unreadA.getId()).orElseThrow().isRead()).isTrue();

        String foreign = mockMvc.perform(patch("/api/v1/notifications/{id}/read", unreadB.getId())
                        .with(user(adminA)).with(csrf()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(patch("/api/v1/notifications/{id}/read", UUID.randomUUID())
                        .with(user(adminA)).with(csrf()))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(foreign).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknown).get("message").asText());
        assertThat(notifications.findById(unreadB.getId()).orElseThrow().isRead()).isFalse();

        Notification secondUnreadA = notifications.saveAndFlush(notification(leadA, "Second unread A"));
        mockMvc.perform(patch("/api/v1/notifications/read-all").with(user(adminA)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(notifications.findById(secondUnreadA.getId()).orElseThrow().isRead()).isTrue();
        assertThat(notifications.findById(unreadB.getId()).orElseThrow().isRead()).isFalse();
    }

    @Test
    void qualificationHistoryAndRetryAuthorizationRemainWorkspaceIsolated() throws Exception {
        Lead failedA = leads.saveAndFlush(failedLead(workspaceA, "Failed A", "failed-a@example.invalid"));
        Lead failedB = leads.saveAndFlush(failedLead(workspaceB, "Failed B", "failed-b@example.invalid"));
        QualificationAttempt firstA = terminalAttempt(failedA);
        QualificationAttempt firstB = terminalAttempt(failedB);

        mockMvc.perform(get("/api/v1/leads/{id}/qualification-attempts", failedA.getId())
                        .with(user(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(firstA.getId().toString()));
        String foreign = mockMvc.perform(get("/api/v1/leads/{id}/qualification-attempts", failedB.getId())
                        .with(user(adminA)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(get("/api/v1/leads/{id}/qualification-attempts", UUID.randomUUID())
                        .with(user(adminA)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(foreign).get("message").asText())
                .isEqualTo(objectMapper.readTree(unknown).get("message").asText());

        mockMvc.perform(post("/api/v1/leads/{id}/qualification-retry", failedB.getId())
                        .with(user(adminA)).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + failedB.getVersion() + "}"))
                .andExpect(status().isNotFound());
        assertThat(attempts.findHistoryByLeadAndWorkspace(
                failedB.getId(), workspaceB.getId())).hasSize(1);

        mockMvc.perform(post("/api/v1/leads/{id}/qualification-retry", failedA.getId())
                        .with(user(adminA)).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + failedA.getVersion() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.attemptNumber").value(2));
        QualificationAttempt retry = attempts.findLatestByLeadAndWorkspace(
                failedA.getId(), workspaceA.getId()).orElseThrow();
        assertThat(retry.getWorkspace().getId()).isEqualTo(workspaceA.getId());
        QualificationDispatchOutbox retryOutbox = outboxes.findAll().stream()
                .filter(outbox -> outbox.getAttempt().getId().equals(retry.getId())).findFirst().orElseThrow();
        assertThat(retryOutbox.getWorkspace().getId()).isEqualTo(workspaceA.getId());
        assertThat(firstB.getStatus()).isEqualTo(QualificationAttemptStatus.FAILED);
    }

    @Test
    void unavailableWorkspaceContextsFailClosed() throws Exception {
        for (Workspace unavailable : java.util.List.of(pending, suspended)) {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                    "unavailable@example.invalid", "Unavailable", UserRole.ADMIN,
                    unavailable.getId(), null, true);
            mockMvc.perform(get("/api/v1/notifications").with(user(principal)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(principal)))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/v1/leads/{id}/qualification-attempts", UUID.randomUUID())
                            .with(user(principal)))
                    .andExpect(status().isUnauthorized());
        }
        AuthenticatedPrincipal unknown = new AuthenticatedPrincipal(UUID.randomUUID(),
                "unknown@example.invalid", "Unknown", UserRole.ADMIN, UUID.randomUUID(), null, true);
        mockMvc.perform(get("/api/v1/notifications").with(user(unknown)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corruptedParentChildOwnershipFailsClosed() throws Exception {
        Lead leadA = leads.saveAndFlush(lead(workspaceA, "Mismatch A", "mismatch-a@example.invalid"));
        Lead leadB = leads.saveAndFlush(lead(workspaceB, "Mismatch B", "mismatch-b@example.invalid"));
        Notification mismatchedNotification = notification(leadB, "Mismatched notification");
        ReflectionTestUtils.setField(mismatchedNotification, "workspace", workspaceA);
        notifications.saveAndFlush(mismatchedNotification);

        mockMvc.perform(get("/api/v1/notifications").with(user(adminA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", mismatchedNotification.getId())
                        .with(user(adminA)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(notifications.findById(mismatchedNotification.getId()).orElseThrow().isRead()).isFalse();

        ReflectionTestUtils.setField(leadA, "status", LeadStatus.AUTOMATION_FAILED);
        leads.saveAndFlush(leadA);
        QualificationAttempt mismatchedAttempt = QualificationAttempt.create(leadA, 1);
        mismatchedAttempt.fail(QualificationFailureCode.UNKNOWN,
                "Automated qualification did not complete.", null, Instant.now());
        ReflectionTestUtils.setField(mismatchedAttempt, "workspace", workspaceB);
        attempts.saveAndFlush(mismatchedAttempt);

        mockMvc.perform(get("/api/v1/leads/{id}/qualification-attempts", leadA.getId())
                        .with(user(adminA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/leads/{id}/qualification-retry", leadA.getId())
                        .with(user(adminA)).with(csrf()).contentType("application/json")
                        .content("{\"version\":" + leadA.getVersion() + "}"))
                .andExpect(status().isNotFound());
        assertThat(attempts.count()).isOne();
        assertThat(outboxes.count()).isZero();
    }

    private Workspace workspace(String slug, WorkspaceStatus status) {
        return workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(), slug, slug, status));
    }

    private AuthenticatedPrincipal administrator(Workspace workspace, String email) {
        users.saveAndFlush(User.createAdministrator(workspace, email, "Administrator",
                passwordEncoder.encode("Synthetic-password-42!")));
        return (AuthenticatedPrincipal) userDetails.loadUserByUsername(email);
    }

    private Lead lead(Workspace workspace, String name, String email) {
        return Lead.create(workspace, name, email, null, "Tenant Co", "Tenant service", null,
                new BigDecimal("1000.00"), LocalDate.now().plusDays(7),
                "A sufficiently detailed notification qualification isolation request.", "test");
    }

    private Lead failedLead(Workspace workspace, String name, String email) {
        Lead lead = lead(workspace, name, email);
        ReflectionTestUtils.setField(lead, "status", LeadStatus.AUTOMATION_FAILED);
        return lead;
    }

    private Notification notification(Lead lead, String title) {
        return Notification.create(NotificationType.NEW_LEAD, NotificationSeverity.INFO,
                title, "Workspace-isolated notification message.", lead);
    }

    private QualificationAttempt terminalAttempt(Lead lead) {
        QualificationAttempt attempt = QualificationAttempt.create(lead, 1);
        attempt.fail(QualificationFailureCode.UNKNOWN,
                "Automated qualification did not complete.", null, Instant.now());
        return attempts.saveAndFlush(attempt);
    }
}
