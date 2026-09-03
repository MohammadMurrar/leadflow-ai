package com.mohammadmurrar.leadflow.email;

import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.lead.api.CreateLeadRequest;
import com.mohammadmurrar.leadflow.notification.NotificationRepository;
import com.mohammadmurrar.leadflow.notification.NotificationType;
import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.qualification.api.*;
import com.mohammadmurrar.leadflow.service.*;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.*;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.user.UserRole;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "leadflow.qualification-reliability.retry-enabled=true")
class EmailIntentIntegrationTest {
    @Autowired LeadService leadService;
    @Autowired PublicInquiryService publicInquiryService;
    @Autowired WorkspaceSettingsService settingsService;
    @Autowired EmailOutboxRepository emailOutbox;
    @Autowired LeadRepository leads;
    @Autowired QualificationAttemptRepository attempts;
    @Autowired QualificationDispatchOutboxRepository dispatchOutbox;
    @Autowired QualificationAttemptService qualificationService;
    @Autowired NotificationRepository notifications;
    @Autowired ServiceOfferingRepository services;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired TransactionTemplate transactions;
    @MockitoSpyBean EmailOutboxService outboxService;
    @Autowired WorkspaceRepository workspaces;
    private Workspace workspace;

    @BeforeEach
    void prepare() {
        reset(outboxService);
        emailOutbox.deleteAll();
        dispatchOutbox.deleteAll();
        attempts.deleteAll();
        notifications.deleteAll();
        leads.deleteAll();
        services.deleteAll();
        users.deleteAll();
        workspace = workspaces.findByPublicSlugAndStatus("leadflow-ai", WorkspaceStatus.ACTIVE)
                .orElseGet(() -> workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "leadflow-ai", "Legacy Workspace", WorkspaceStatus.ACTIVE)));
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "test-admin@example.invalid", "Test Admin", UserRole.ADMIN,
                workspace.getId(), null, true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
        users.saveAndFlush(User.createAdministrator(workspace, "fallback-admin@example.invalid",
                "Fallback Administrator", passwordEncoder.encode("Synthetic-password-42!")));
        configureRecipients(List.of("zeta@example.invalid", "alpha@example.invalid"));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administrativeAndPublicCreationPersistOneRecipientSnapshotEach() {
        leadService.create(adminRequest("admin-step3@example.invalid"));
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getRecipient)
                .containsExactlyInAnyOrder("alpha@example.invalid", "zeta@example.invalid");

        ServiceOffering offering = services.saveAndFlush(
                ServiceOffering.create(workspace, "Public Step 3 Service", "Public test service"));
        publicInquiryService.submit(publicRequest("public-step3@example.invalid", offering.getId(), null));
        assertThat(emailOutbox.findAll()).hasSize(4);

        publicInquiryService.submit(publicRequest("  PUBLIC-STEP3@EXAMPLE.INVALID  ", offering.getId(), null));
        publicInquiryService.submit(publicRequest("honeypot-step3@example.invalid", offering.getId(), "filled"));
        assertThat(emailOutbox.findAll()).hasSize(4);
        assertThat(leads.count()).isEqualTo(2);
    }

    @Test
    void noExplicitRecipientsUseAdministratorFallbackWithoutSynchronousDelivery() {
        configureRecipients(List.of());
        leadService.create(adminRequest("no-recipient-step3@example.invalid"));

        assertThat(leads.count()).isOne();
        assertThat(emailOutbox.findAll()).singleElement().satisfies(intent -> {
            assertThat(intent.getRecipient()).isEqualTo("fallback-admin@example.invalid");
            assertThat(intent.getTemplateType()).isEqualTo(EmailTemplateType.NEW_INQUIRY);
            assertThat(intent.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
            assertThat(intent.getDeliveryCount()).isZero();
        });
    }

    @Test
    void noEligibleRecipientStillCommitsLeadWithoutEmailRows() {
        configureRecipients(List.of());
        users.deleteAll();

        leadService.create(adminRequest("no-eligible-recipient@example.invalid"));

        assertThat(leads.count()).isOne();
        assertThat(emailOutbox.count()).isZero();
    }

    @Test
    void publicInquiryUsesAdministratorFallbackAndKeepsRecipientOutOfAcknowledgement() {
        configureRecipients(List.of());
        ServiceOffering offering = services.saveAndFlush(
                ServiceOffering.create(workspace, "Fallback Public Service", "Public test service"));

        var response = publicInquiryService.submit(publicRequest(
                "public-fallback@example.invalid", offering.getId(), null));

        assertThat(leads.findAll()).singleElement().satisfies(lead ->
                assertThat(lead.getSource()).isEqualTo("public-inquiry"));
        assertThat(emailOutbox.findAll()).singleElement().satisfies(intent -> {
            assertThat(intent.getRecipient()).isEqualTo("fallback-admin@example.invalid");
            assertThat(intent.getTemplateType()).isEqualTo(EmailTemplateType.NEW_INQUIRY);
            assertThat(intent.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
            assertThat(intent.getDeliveryCount()).isZero();
        });
        assertThat(response.message()).doesNotContain("fallback-admin@example.invalid");
    }

    @Test
    void multipleEnabledAdministratorsProduceOneDeterministicallyOrderedIntentEach() {
        configureRecipients(List.of());
        users.saveAndFlush(User.createAdministrator(workspace, "alpha-admin@example.invalid",
                "Alpha Administrator", passwordEncoder.encode("Synthetic-password-42!")));

        leadService.create(adminRequest("multiple-admins@example.invalid"));

        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getRecipient)
                .containsExactly("alpha-admin@example.invalid", "fallback-admin@example.invalid");
    }

    @Test
    void recipientChangesDoNotAlterPersistedSnapshots() {
        leadService.create(adminRequest("snapshot-step3@example.invalid"));
        configureRecipients(List.of("replacement@example.invalid"));

        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getRecipient)
                .containsExactlyInAnyOrder("alpha@example.invalid", "zeta@example.invalid");
    }

    @Test
    void enqueueFailureRollsBackLeadNotificationAttemptAndBothOutboxes() {
        long leadCount = leads.count();
        long notificationCount = notifications.count();
        long attemptCount = attempts.count();
        long dispatchCount = dispatchOutbox.count();
        long emailCount = emailOutbox.count();
        doThrow(new EmailOutboxService.EmailOutboxPersistenceException()).when(outboxService)
                .enqueue(any(), anyString(), any(), anyString(), any());

        assertThatThrownBy(() -> transactions.executeWithoutResult(ignored ->
                leadService.create(adminRequest("rollback-step3@example.invalid"))))
                .isInstanceOf(EmailOutboxService.EmailOutboxPersistenceException.class)
                .hasMessage("Email outbox could not be persisted");

        assertThat(leads.count()).isEqualTo(leadCount);
        assertThat(notifications.count()).isEqualTo(notificationCount);
        assertThat(attempts.count()).isEqualTo(attemptCount);
        assertThat(dispatchOutbox.count()).isEqualTo(dispatchCount);
        assertThat(emailOutbox.count()).isEqualTo(emailCount);
    }

    @Test
    void qualificationEnqueueFailureRollsBackTerminalTransition() {
        var created = leadService.create(adminRequest("qualification-rollback-step3@example.invalid"));
        QualificationAttempt attempt = attempts.findLatestByLeadAndWorkspace(
                        created.id(), workspace.getId())
                .orElseThrow();
        qualificationService.start(created.id(), attempt.getId(),
                new QualificationStartRequest("rollback-execution"));
        doThrow(new EmailOutboxService.EmailOutboxPersistenceException()).when(outboxService)
                .enqueue(eq(EmailTemplateType.QUALIFICATION_COMPLETED), anyString(), any(), anyString(), any());

        assertThatThrownBy(() -> qualificationService.succeed(created.id(), attempt.getId(),
                new QualificationSuccessRequest(90, LeadPriority.MEDIUM, "Qualified",
                        "forbidden-rollback-summary", "forbidden-rollback-reply", "rollback-execution")))
                .isInstanceOf(EmailOutboxService.EmailOutboxPersistenceException.class)
                .hasMessage("Email outbox could not be persisted");

        assertThat(leads.findById(created.id()).orElseThrow().getStatus()).isEqualTo(LeadStatus.QUALIFYING);
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(QualificationAttemptStatus.PROCESSING);
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getTemplateType)
                .containsOnly(EmailTemplateType.NEW_INQUIRY);
        assertThat(notifications.findAll().stream()
                .filter(notification -> notification.getType() == NotificationType.LEAD_QUALIFIED)
                .count()).isZero();
    }

    @Test
    void successfulCallbackIsIdempotentAndPreservesDomainOutcome() {
        var created = leadService.create(adminRequest("success-step3@example.invalid"));
        QualificationAttempt attempt = attempts.findLatestByLeadAndWorkspace(
                        created.id(), workspace.getId())
                .orElseThrow();
        qualificationService.start(created.id(), attempt.getId(),
                new QualificationStartRequest("success-execution"));
        QualificationSuccessRequest request = new QualificationSuccessRequest(92, LeadPriority.MEDIUM,
                "Qualified", "forbidden-ai-summary", "forbidden-recommended-reply",
                "success-execution");

        qualificationService.succeed(created.id(), attempt.getId(), request);
        qualificationService.succeed(created.id(), attempt.getId(), request);

        assertThat(leads.findById(created.id()).orElseThrow().getStatus()).isEqualTo(LeadStatus.QUALIFIED);
        assertThat(attempts.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(QualificationAttemptStatus.SUCCEEDED);
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getTemplateType)
                .containsExactlyInAnyOrder(EmailTemplateType.NEW_INQUIRY, EmailTemplateType.NEW_INQUIRY,
                        EmailTemplateType.QUALIFICATION_COMPLETED,
                        EmailTemplateType.QUALIFICATION_COMPLETED);
        assertThat(notifications.findAll().stream()
                .filter(notification -> notification.getType() == NotificationType.LEAD_QUALIFIED)
                .count()).isOne();
        assertThat(attempts.count()).isOne();
        assertThat(dispatchOutbox.count()).isOne();
    }

    @Test
    void failureRetryAndSecondAttemptSuccessCreateOnlyTerminalAttemptIntents() {
        var created = leadService.create(adminRequest("retry-step3@example.invalid"));
        QualificationAttempt first = attempts.findLatestByLeadAndWorkspace(
                        created.id(), workspace.getId())
                .orElseThrow();
        qualificationService.start(created.id(), first.getId(), new QualificationStartRequest("failure-execution"));
        QualificationFailureRequest failure = new QualificationFailureRequest(
                QualificationFailureCode.AI_PROVIDER_ERROR, "forbidden-provider-detail", "failure-execution");
        qualificationService.fail(created.id(), first.getId(), failure);
        qualificationService.fail(created.id(), first.getId(), failure);
        assertThat(emailOutbox.count()).isEqualTo(4);

        Lead failedLead = leads.findById(created.id()).orElseThrow();
        qualificationService.retry(created.id(), failedLead.getVersion());
        assertThat(emailOutbox.count()).isEqualTo(4);
        QualificationAttempt second = attempts.findLatestByLeadAndWorkspace(
                        created.id(), workspace.getId())
                .orElseThrow();
        assertThat(second.getAttemptNumber()).isEqualTo(2);
        qualificationService.start(created.id(), second.getId(), new QualificationStartRequest("second-execution"));
        qualificationService.succeed(created.id(), second.getId(), new QualificationSuccessRequest(
                84, LeadPriority.MEDIUM, "Qualified", "forbidden-second-summary",
                "forbidden-second-reply", "second-execution"));

        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getTemplateType)
                .containsExactlyInAnyOrder(EmailTemplateType.NEW_INQUIRY, EmailTemplateType.NEW_INQUIRY,
                        EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION,
                        EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION,
                        EmailTemplateType.QUALIFICATION_COMPLETED,
                        EmailTemplateType.QUALIFICATION_COMPLETED);
        assertThat(attempts.count()).isEqualTo(2);
        assertThat(dispatchOutbox.count()).isEqualTo(2);
    }

    @Test
    void timeoutReconciliationIsIdempotent() {
        var created = leadService.create(adminRequest("timeout-step3@example.invalid"));
        QualificationAttempt attempt = attempts.findLatestByLeadAndWorkspace(
                        created.id(), workspace.getId())
                .orElseThrow();
        assertThat(qualificationService.timeOut(attempt.getId())).isTrue();
        assertThat(qualificationService.timeOut(attempt.getId())).isFalse();
        assertThat(emailOutbox.findAll()).extracting(EmailOutbox::getTemplateType)
                .containsExactlyInAnyOrder(EmailTemplateType.NEW_INQUIRY, EmailTemplateType.NEW_INQUIRY,
                        EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION,
                        EmailTemplateType.QUALIFICATION_NEEDS_ATTENTION);
        assertThat(notifications.findAll().stream()
                .filter(notification -> notification.getType() == NotificationType.AUTOMATION_FAILED)
                .count()).isOne();
        assertThat(attempts.count()).isOne();
        assertThat(dispatchOutbox.count()).isOne();
    }

    private void configureRecipients(List<String> recipients) {
        var workspace = settingsService.findWorkspace();
        settingsService.update(new UpdateWorkspaceSettingsRequest(workspace.version(),
                workspace.workspaceName(), workspace.contactEmail(), workspace.description(),
                workspace.publicBrandName(), workspace.publicTagline(), workspace.publicLogoPath(),
                workspace.timeZone(), workspace.currency(), workspace.responseTimeText(),
                workspace.privacyPolicyUrl(), workspace.privacyNoticeText(),
                workspace.privacyNoticeVersion(), recipients));
    }

    private CreateLeadRequest adminRequest(String email) {
        return new CreateLeadRequest("Step 3 Lead", email, null, "Step 3 Company",
                "Step 3 Service", new BigDecimal("5000.00"), LocalDate.now().plusDays(7),
                "A sufficiently detailed Step 3 inquiry message.", "admin");
    }

    private PublicLeadRequest publicRequest(String email, java.util.UUID serviceId, String website) {
        return new PublicLeadRequest("Public Step 3 Lead", email, null, "Public Company",
                serviceId, new BigDecimal("5000.00"), LocalDate.now().plusDays(7),
                "A sufficiently detailed public Step 3 inquiry message.", website);
    }
}
