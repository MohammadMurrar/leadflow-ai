package com.mohammadmurrar.leadflow.workspace;

import com.mohammadmurrar.leadflow.LeadFlowApplication;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.LeadService;
import com.mohammadmurrar.leadflow.lead.DuplicateLeadException;
import com.mohammadmurrar.leadflow.lead.LeadRepository;
import com.mohammadmurrar.leadflow.lead.LeadStatus;
import com.mohammadmurrar.leadflow.lead.QualificationState;
import com.mohammadmurrar.leadflow.lead.api.CreateLeadRequest;
import com.mohammadmurrar.leadflow.dashboard.DashboardService;
import com.mohammadmurrar.leadflow.analytics.AnalyticsService;
import com.mohammadmurrar.leadflow.notification.NotificationRepository;
import com.mohammadmurrar.leadflow.notification.NotificationService;
import com.mohammadmurrar.leadflow.passwordreset.PasswordResetRequestService;
import com.mohammadmurrar.leadflow.publicapi.PublicInquiryService;
import com.mohammadmurrar.leadflow.publicapi.PublicWorkspaceResolver;
import com.mohammadmurrar.leadflow.publicapi.api.PublicLeadRequest;
import com.mohammadmurrar.leadflow.provisioning.WorkspaceProvisioningCommand;
import com.mohammadmurrar.leadflow.provisioning.WorkspaceProvisioningService;
import com.mohammadmurrar.leadflow.security.AuthenticatedPrincipal;
import com.mohammadmurrar.leadflow.service.ServiceOfferingService;
import com.mohammadmurrar.leadflow.service.api.CreateServiceRequest;
import com.mohammadmurrar.leadflow.settings.WorkspaceSettingsService;
import com.mohammadmurrar.leadflow.settings.api.UpdateWorkspaceSettingsRequest;
import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import com.mohammadmurrar.leadflow.user.UserRole;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptRepository;
import com.mohammadmurrar.leadflow.qualification.QualificationAttemptService;
import com.mohammadmurrar.leadflow.qualification.QualificationFailureCode;
import com.mohammadmurrar.leadflow.qualification.api.QualificationFailureRequest;
import com.mohammadmurrar.leadflow.qualification.api.QualificationStartRequest;
import com.mohammadmurrar.leadflow.email.*;
import com.mohammadmurrar.leadflow.qualification.QualificationAttempt;
import com.mohammadmurrar.leadflow.qualification.QualificationDispatchOutbox;
import com.mohammadmurrar.leadflow.qualification.QualificationDispatchOutboxRepository;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WorkspaceFoundationMigrationTest {
    private static final String BASE_URL = System.getenv("LEADFLOW_MYSQL_TEST_JDBC_BASE");
    private static final String USERNAME = System.getenv("LEADFLOW_MYSQL_TEST_USERNAME");
    private static final String PASSWORD = System.getenv("LEADFLOW_MYSQL_TEST_PASSWORD");
    private static final String[] OWNED_TABLES = {
            "workspace_settings", "workspace_notification_recipients", "users", "services",
            "leads", "notifications", "qualification_attempts",
            "qualification_dispatch_outbox", "email_outbox", "password_reset_requests"
    };

    @Test
    void ownerProvisioningIsAtomicConcurrentAndIsolatedOnMySql84() throws Exception {
        try (Database database = newDatabase()) {
            assertThat(flyway(database.url()).migrate().migrationsExecuted).isEqualTo(14);
            try (var context = application(database.url(), false)) {
                WorkspaceProvisioningService provisioning = context.getBean(WorkspaceProvisioningService.class);
                assertThat(provisioning.preview(provisioningCommand(
                        "leadflow-ai", "owner-a@example.invalid")).slugAvailable()).isTrue();
                provisioning.provision(provisioningCommand("leadflow-ai", "owner-a@example.invalid"));
                provisioning.provision(provisioningCommand("mysql-client-b", "mysql-b@example.invalid"));

                var workspaces = context.getBean(WorkspaceRepository.class);
                var users = context.getBean(UserRepository.class);
                var services = context.getBean(com.mohammadmurrar.leadflow.service.ServiceOfferingRepository.class);
                Workspace workspaceA = workspaces.findByPublicSlugAndStatus(
                        "leadflow-ai", WorkspaceStatus.ACTIVE).orElseThrow();
                Workspace workspaceB = workspaces.findByPublicSlugAndStatus(
                        "mysql-client-b", WorkspaceStatus.ACTIVE).orElseThrow();
                assertThat(users.findByNormalizedEmail("owner-a@example.invalid").orElseThrow()
                        .getWorkspace().getId()).isEqualTo(workspaceA.getId());
                assertThat(services.existsByWorkspaceIdAndNormalizedName(
                        workspaceA.getId(), "shared mysql service")).isTrue();
                assertThat(services.existsByWorkspaceIdAndNormalizedName(
                        workspaceB.getId(), "shared mysql service")).isTrue();

                var authentication = context.getBean(org.springframework.security.authentication.AuthenticationManager.class)
                        .authenticate(org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                        .unauthenticated("owner-a@example.invalid", "Correct-Horse-42!"));
                assertThat(((AuthenticatedPrincipal) authentication.getPrincipal()).workspaceId())
                        .isEqualTo(workspaceA.getId());

                long beforeRollback = workspaces.count();
                assertThatThrownBy(() -> provisioning.provision(new WorkspaceProvisioningCommand(
                        "Rollback", "mysql-rollback", null, "Admin", "mysql-rollback@example.invalid",
                        "Correct-Horse-42!".toCharArray(), List.of(
                        new WorkspaceProvisioningCommand.InitialService("Invalid", "x".repeat(1001), true)),
                        List.of())))
                        .isInstanceOf(WorkspaceProvisioningService.InvalidProvisioningCommandException.class);
                assertThat(workspaces.count()).isEqualTo(beforeRollback);
                assertThat(users.findByNormalizedEmail("mysql-rollback@example.invalid")).isEmpty();

                try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                    var start = new java.util.concurrent.CountDownLatch(1);
                    var first = executor.submit(() -> provisionAfter(start, provisioning,
                            provisioningCommand("mysql-race", "mysql-race-a@example.invalid")));
                    var second = executor.submit(() -> provisionAfter(start, provisioning,
                            provisioningCommand("mysql-race", "mysql-race-b@example.invalid")));
                    start.countDown();
                    assertThat(java.util.stream.Stream.of(first.get(), second.get())
                            .filter(Boolean::booleanValue).count()).isEqualTo(1);
                }
            }
            try (Connection connection = database.connection()) {
                assertThat(count(connection, "SELECT COUNT(*) FROM (SELECT workspace.id FROM workspaces workspace "
                        + "LEFT JOIN users admin ON admin.workspace_id = workspace.id AND admin.role = 'ADMIN' "
                        + "WHERE workspace.public_slug LIKE 'mysql-%' GROUP BY workspace.id HAVING COUNT(admin.id) <> 1) invalid"))
                        .isZero();
                assertThat(count(connection, "SELECT COUNT(*) FROM workspaces workspace "
                        + "LEFT JOIN workspace_settings settings ON settings.workspace_id = workspace.id "
                        + "WHERE workspace.public_slug LIKE 'mysql-%' AND settings.id IS NULL")).isZero();
                assertThat(count(connection, "SELECT COUNT(*) FROM users user_account JOIN workspaces workspace "
                        + "ON workspace.id = user_account.workspace_id WHERE user_account.normalized_email LIKE 'mysql-%' "
                        + "AND workspace.public_slug NOT LIKE 'mysql-%'")).isZero();
                assertOwnershipComplete(connection);
                assertParentOwnershipMatches(connection);
            }
        }
    }

    private WorkspaceProvisioningCommand provisioningCommand(String slug, String email) {
        return new WorkspaceProvisioningCommand("MySQL Client", slug, "Public description",
                "MySQL Admin", email, "Correct-Horse-42!".toCharArray(),
                List.of(new WorkspaceProvisioningCommand.InitialService(
                        "Shared MySQL Service", "Description", true)),
                List.of("mysql-notify@example.invalid"));
    }

    private boolean provisionAfter(java.util.concurrent.CountDownLatch start,
            WorkspaceProvisioningService provisioning, WorkspaceProvisioningCommand command) {
        try {
            start.await();
            provisioning.provision(command);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException expected) {
            return false;
        }
    }

    @Test
    void slugSelectedPublicInquiryIsIsolatedOnMySql84() throws Exception {
        try (Database database = newDatabase()) {
            assertThat(flyway(database.url()).migrate().migrationsExecuted).isEqualTo(14);
            try (var context = application(database.url(), false)) {
                WorkspaceRepository workspaces = context.getBean(WorkspaceRepository.class);
                var settings = context.getBean(
                        com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository.class);
                var services = context.getBean(
                        com.mohammadmurrar.leadflow.service.ServiceOfferingRepository.class);
                Workspace workspaceA = workspaces.findByPublicSlugAndStatus(
                        "leadflow-ai", WorkspaceStatus.ACTIVE).orElseThrow();
                Workspace workspaceB = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "acme-consulting", "Workspace B", WorkspaceStatus.ACTIVE));
                Workspace pending = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "pending-public", "Pending", WorkspaceStatus.PENDING));
                Workspace suspended = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "suspended-public", "Suspended", WorkspaceStatus.SUSPENDED));

                var transactions = new TransactionTemplate(
                        context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                transactions.executeWithoutResult(status -> {
                    var settingsA = settings.findByWorkspaceId(workspaceA.getId()).orElseThrow();
                    settingsA.update("Public A", null, "A-only branding", "Brand A", null, null,
                            "UTC", "USD", "Response", null, null, null,
                            java.util.List.of("a@example.invalid"));
                    settings.saveAndFlush(settingsA);
                });
                var settingsB = com.mohammadmurrar.leadflow.settings.WorkspaceSettings
                        .createNeutral(workspaceB, (byte) 2);
                settingsB.update("Public B", null, "B-only branding", "Brand B", null, null,
                        "UTC", "USD", "Response", null, null, null,
                        java.util.List.of("b@example.invalid"));
                settings.saveAndFlush(settingsB);
                var serviceA = services.saveAndFlush(com.mohammadmurrar.leadflow.service.ServiceOffering
                        .create(workspaceA, "Shared Public Service", "A"));
                var serviceB = services.saveAndFlush(com.mohammadmurrar.leadflow.service.ServiceOffering
                        .create(workspaceB, "Shared Public Service", "B"));

                PublicWorkspaceResolver resolver = context.getBean(PublicWorkspaceResolver.class);
                PublicInquiryService publicInquiry = context.getBean(PublicInquiryService.class);
                assertThat(publicInquiry.configuration(resolver.resolve("leadflow-ai")).services())
                        .extracting("id").containsExactly(serviceA.getId());
                assertThat(publicInquiry.configuration(resolver.resolve("acme-consulting")).services())
                        .extracting("id").containsExactly(serviceB.getId());
                assertThatThrownBy(() -> resolver.resolve(pending.getPublicSlug()))
                        .isInstanceOf(NotFoundException.class);
                assertThatThrownBy(() -> resolver.resolve(suspended.getPublicSlug()))
                        .isInstanceOf(NotFoundException.class);

                String sharedEmail = "same-address@example.invalid";
                publicInquiry.submit(resolver.resolve("leadflow-ai"), publicRequest(sharedEmail, serviceA.getId()));
                publicInquiry.submit(resolver.resolve("acme-consulting"), publicRequest(sharedEmail, serviceB.getId()));
                assertThatThrownBy(() -> publicInquiry.submit(resolver.resolve("leadflow-ai"),
                        publicRequest("cross@example.invalid", serviceB.getId())))
                        .isInstanceOf(NotFoundException.class);

                try (Connection connection = database.connection()) {
                    assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE source = 'public-inquiry'"))
                            .isEqualTo(2);
                    assertThat(count(connection, "SELECT COUNT(*) FROM leads lead_row JOIN services service "
                            + "ON service.id = lead_row.service_id WHERE lead_row.workspace_id <> service.workspace_id"))
                            .isZero();
                    assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE workspace_id IS NULL")).isZero();
                    assertThat(count(connection, "SELECT COUNT(*) FROM notifications notification JOIN leads lead_row "
                            + "ON lead_row.id = notification.lead_id WHERE notification.workspace_id <> lead_row.workspace_id"))
                            .isZero();
                    assertThat(count(connection, "SELECT COUNT(*) FROM qualification_attempts attempt JOIN leads lead_row "
                            + "ON lead_row.id = attempt.lead_id WHERE attempt.workspace_id <> lead_row.workspace_id"))
                            .isZero();
                    assertThat(count(connection, "SELECT COUNT(*) FROM qualification_dispatch_outbox dispatch JOIN "
                            + "qualification_attempts attempt ON attempt.id = dispatch.attempt_id "
                            + "WHERE dispatch.workspace_id <> attempt.workspace_id")).isZero();
                    assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox email JOIN leads lead_row "
                            + "ON lead_row.id = email.lead_id WHERE email.workspace_id <> lead_row.workspace_id"))
                            .isZero();
                }
            }
        }
    }

    private PublicLeadRequest publicRequest(String email, UUID serviceId) {
        return new PublicLeadRequest("Public audit", email, null, null, serviceId, null, null,
                "A sufficiently detailed public workspace isolation inquiry.", null);
    }

    @Test
    void freshMigrationCreatesOneLegacyWorkspaceAndValidOwnershipFoundation() throws Exception {
        try (Database database = newDatabase()) {
            Flyway flyway = flyway(database.url());
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(14);

            try (Connection connection = database.connection()) {
                assertLegacyWorkspace(connection);
                assertThat(count(connection, "SELECT COUNT(*) FROM workspace_settings "
                        + "WHERE workspace_id = (SELECT id FROM workspaces WHERE public_slug = 'leadflow-ai')"))
                        .isEqualTo(1);
                assertOwnershipComplete(connection);
                assertForeignKeysRejectUnknownWorkspace(connection);
                assertDuplicateSlugRejected(connection);
                assertWorkspaceDeleteRestricted(connection);
                assertPerWorkspaceSettingsSchema(connection);
                assertPerWorkspaceSettingsConstraints(connection);
                assertPerWorkspaceServiceSchema(connection);
                assertPerWorkspaceServiceConstraints(connection);
            }

            assertThat(flyway.migrate().migrationsExecuted).isZero();
            assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
            assertApplicationStartsAgainstV14(database.url());
        }
    }

    @Test
    void upgradeMigrationPreservesLegacyRowsAndBackfillsParentConsistentOwnership() throws Exception {
        try (Database database = newDatabase()) {
            Flyway throughV12 = Flyway.configure().dataSource(database.url(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("12"))
                    .load();
            Flyway throughV11 = Flyway.configure().dataSource(database.url(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("11"))
                    .load();
            assertThat(throughV11.migrate().migrationsExecuted).isEqualTo(11);

            Map<String, Long> before;
            try (Connection connection = database.connection()) {
                insertLegacyFixture(connection);
                before = counts(connection);
            }

            assertThat(throughV12.migrate().migrationsExecuted).isEqualTo(1);

            Flyway throughV13 = Flyway.configure().dataSource(database.url(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("13"))
                    .load();
            assertThat(throughV13.migrate().migrationsExecuted).isEqualTo(1);

            Flyway flyway = flyway(database.url());
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            try (Connection connection = database.connection()) {
                assertLegacyWorkspace(connection);
                assertThat(counts(connection)).containsExactlyEntriesOf(before);
                assertOwnershipComplete(connection);
                assertParentOwnershipMatches(connection);
                assertPerWorkspaceSettingsSchema(connection);
                assertPerWorkspaceServiceSchema(connection);
                assertThat(text(connection, "SELECT role FROM users WHERE email = 'legacy-admin@example.invalid'"))
                        .isEqualTo("ADMIN");
                assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE id = UUID_TO_BIN"
                        + "('00000000-0000-0000-0000-000000000301')"))
                        .isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox WHERE id IN "
                        + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000701'), "
                        + "UUID_TO_BIN('00000000-0000-0000-0000-000000000702'))"))
                        .isEqualTo(2);
            }

            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }

    @Test
    void productionCreationPathsPersistCompleteAndIsolatedWorkspaceOwnership() throws Exception {
        try (Database database = newDatabase()) {
            assertThat(flyway(database.url()).migrate().migrationsExecuted).isEqualTo(14);
            try (var context = application(database.url(), true)) {
            WorkspaceRepository workspaces = context.getBean(WorkspaceRepository.class);
            Workspace legacy = workspaces.findByPublicSlugAndStatus("leadflow-ai", WorkspaceStatus.ACTIVE)
                    .orElseThrow();
            authenticate(legacy);

            WorkspaceSettingsService settings = context.getBean(WorkspaceSettingsService.class);
            var currentSettings = settings.findWorkspace();
            settings.update(new UpdateWorkspaceSettingsRequest(currentSettings.version(),
                    "Ownership Audit A", null, "Workspace A public description",
                    "Ownership Audit A", "Workspace A public tagline", "/assets/audit-a.svg",
                    "UTC", "USD", "Audit response time", null, null, null,
                    java.util.List.of("audit-recipient@example.invalid")));

            ServiceOfferingService services = context.getBean(ServiceOfferingService.class);
            UUID serviceA = services.create(new CreateServiceRequest(
                    "Workspace A Service", "Workspace A service")).id();
            LeadService leads = context.getBean(LeadService.class);
            leads.create(new CreateLeadRequest("Admin-created lead", "admin-lead@example.invalid",
                    null, null, serviceA, null, null, null,
                    "A sufficiently detailed admin-created ownership audit lead.", "audit"));

            UserRepository users = context.getBean(UserRepository.class);
            org.springframework.core.env.Environment bootstrapEnvironment =
                    org.mockito.Mockito.mock(org.springframework.core.env.Environment.class);
            org.mockito.Mockito.when(bootstrapEnvironment.getProperty(
                    "spring.main.web-application-type", "")).thenReturn("none");
            org.mockito.Mockito.when(bootstrapEnvironment.getProperty(
                    "leadflow.qualification-reliability.dispatcher-enabled", Boolean.class, false))
                    .thenReturn(false);
            new com.mohammadmurrar.leadflow.bootstrap.AdminBootstrapRunner(
                    new com.mohammadmurrar.leadflow.bootstrap.AdminBootstrapProperties(true,
                            "audit-admin@example.invalid", "Audit Admin", "Synthetic-password-42!"),
                    users, context.getBean(com.mohammadmurrar.leadflow.settings.WorkspaceSettingsRepository.class),
                    context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),
                    context.getBean(org.springframework.jdbc.core.JdbcTemplate.class), bootstrapEnvironment,
                    org.mockito.Mockito.mock(org.springframework.context.ConfigurableApplicationContext.class),
                    context.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                    .run(org.mockito.Mockito.mock(org.springframework.boot.ApplicationArguments.class));
            User admin = users.findByNormalizedEmail("audit-admin@example.invalid").orElseThrow();
            assertThat(admin.getWorkspace().getId()).isEqualTo(legacy.getId());
            assertThat(context.getBean(PasswordResetRequestService.class)
                    .request(admin.getEmail(), java.time.Instant.now()))
                    .isEqualTo(PasswordResetRequestService.Result.CREATED);

            Workspace other = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                    "workspace-b-audit", "Workspace B Audit", WorkspaceStatus.ACTIVE));
            authenticate(other);
            UUID serviceB = services.create(new CreateServiceRequest(
                    "Workspace B Service", "Workspace B service")).id();
            SecurityContextHolder.clearContext();

            PublicInquiryService publicInquiry = context.getBean(PublicInquiryService.class);
            var configuration = publicInquiry.configuration();
            assertThat(configuration.workspaceName()).isEqualTo("Ownership Audit A");
            assertThat(configuration.services()).extracting("id").contains(serviceA).doesNotContain(serviceB);
            publicInquiry.submit(new PublicLeadRequest("Public-created lead",
                    "public-lead@example.invalid", null, null, serviceA, null, null,
                    "A sufficiently detailed public ownership audit inquiry.", null));
            assertThatThrownBy(() -> publicInquiry.submit(new PublicLeadRequest(
                    "Cross-workspace lead", "cross-workspace@example.invalid", null, null,
                    serviceB, null, null,
                    "A sufficiently detailed cross-workspace ownership audit inquiry.", null)))
                    .isInstanceOf(NotFoundException.class);

            try (Connection connection = database.connection()) {
                assertCreationPathOwnership(connection, legacy.getId(), other.getId());
            }
            assertBackgroundWorkerIsolation(context, workspaces, legacy, other);
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertBackgroundWorkerIsolation(
            org.springframework.context.ConfigurableApplicationContext context,
            WorkspaceRepository workspaces, Workspace active, Workspace other) {
        LeadRepository leadRepository = context.getBean(LeadRepository.class);
        EmailOutboxService emailOutboxService = context.getBean(EmailOutboxService.class);
        EmailOutboxRepository emailOutboxes = context.getBean(EmailOutboxRepository.class);
        QualificationAttemptRepository attempts = context.getBean(QualificationAttemptRepository.class);
        QualificationDispatchOutboxRepository dispatchOutboxes =
                context.getBean(QualificationDispatchOutboxRepository.class);
        TransactionTemplate transactions = new TransactionTemplate(
                context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        Workspace suspended = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "worker-suspended", "Worker Suspended", WorkspaceStatus.SUSPENDED));
        Workspace pending = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                "worker-pending", "Worker Pending", WorkspaceStatus.PENDING));

        var suspendedLead = workerLead(leadRepository, suspended, "worker-suspended@example.invalid");
        var pendingLead = workerLead(leadRepository, pending, "worker-pending@example.invalid");
        var activeLead = workerLead(leadRepository, active, "worker-active@example.invalid");
        EmailOutbox suspendedEmail = emailOutboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                "held-suspended@example.invalid", suspendedLead, "worker:suspended", java.time.Instant.now().minusSeconds(3));
        EmailOutbox pendingEmail = emailOutboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                "held-pending@example.invalid", pendingLead, "worker:pending", java.time.Instant.now().minusSeconds(2));
        EmailOutbox activeEmail = emailOutboxService.enqueue(EmailTemplateType.NEW_INQUIRY,
                "active-worker@example.invalid", activeLead, "worker:active", java.time.Instant.now().minusSeconds(1));

        QualificationDispatchOutbox suspendedDispatch = workerDispatch(attempts, dispatchOutboxes, suspendedLead);
        QualificationDispatchOutbox pendingDispatch = workerDispatch(attempts, dispatchOutboxes, pendingLead);
        QualificationDispatchOutbox activeDispatch = workerDispatch(attempts, dispatchOutboxes, activeLead);
        Integer eligibleEmailRows = context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).queryForObject("""
                SELECT COUNT(*) FROM email_outbox email
                JOIN workspaces workspace ON workspace.id = email.workspace_id
                LEFT JOIN leads lead_row ON lead_row.id = email.lead_id
                WHERE workspace.status = 'ACTIVE'
                  AND email.template_type <> 'PASSWORD_RESET'
                  AND email.password_reset_request_id IS NULL
                  AND lead_row.workspace_id = email.workspace_id
                  AND email.status = 'PENDING'
                  AND email.available_at <= ?
                """, Integer.class, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(1)));
        assertThat(eligibleEmailRows).isPositive();
        var emailClaims = transactions.execute(status -> emailOutboxes.findClaimableForUpdate(
                java.time.Instant.now().plusSeconds(1), 100));
        var dispatchClaims = transactions.execute(status -> dispatchOutboxes.findClaimableForUpdate(
                java.time.Instant.now().plusSeconds(1), 100));
        assertThat(emailClaims).extracting(EmailOutbox::getId).contains(activeEmail.getId())
                .doesNotContain(suspendedEmail.getId(), pendingEmail.getId());
        assertThat(dispatchClaims).extracting(QualificationDispatchOutbox::getId).contains(activeDispatch.getId())
                .doesNotContain(suspendedDispatch.getId(), pendingDispatch.getId());
        assertThat(emailOutboxes.findById(suspendedEmail.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(emailOutboxes.findById(pendingEmail.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(dispatchOutboxes.findById(suspendedDispatch.getId()).orElseThrow().getDeliveryCount()).isZero();
        assertThat(dispatchOutboxes.findById(pendingDispatch.getId()).orElseThrow().getDeliveryCount()).isZero();

        InquiryEmailRecipientResolver recipients = context.getBean(InquiryEmailRecipientResolver.class);
        assertThat(recipients.resolveNewInquiryRecipients(active.getId()))
                .containsExactly("audit-recipient@example.invalid");
        assertThat(recipients.resolveNewInquiryRecipients(active.getId()))
                .doesNotContain("audit-admin-b@example.invalid");
        context.getBean(UserRepository.class).saveAndFlush(User.createAdministrator(other,
                "audit-admin-b@example.invalid", "Audit Admin B", "{test}audit-b"));
        assertThat(recipients.resolveNewInquiryRecipients(active.getId()))
                .containsExactly("audit-recipient@example.invalid");
    }

    private com.mohammadmurrar.leadflow.lead.Lead workerLead(
            LeadRepository leads, Workspace workspace, String email) {
        var lead = com.mohammadmurrar.leadflow.lead.Lead.create(workspace, "Worker Audit", email,
                null, null, "Workspace A Service", null, null, null,
                "A sufficiently detailed worker audit inquiry.", "audit");
        lead.startQualification();
        return leads.saveAndFlush(lead);
    }

    private QualificationDispatchOutbox workerDispatch(QualificationAttemptRepository attempts,
            QualificationDispatchOutboxRepository outboxes,
            com.mohammadmurrar.leadflow.lead.Lead lead) {
        QualificationAttempt attempt = attempts.saveAndFlush(QualificationAttempt.create(lead, 99));
        return outboxes.saveAndFlush(QualificationDispatchOutbox.create(attempt, java.time.Instant.now()));
    }

    @Test
    void authenticatedLeadPathsRemainIsolatedOnMySql84() throws Exception {
        try (Database database = newDatabase()) {
            assertThat(flyway(database.url()).migrate().migrationsExecuted).isEqualTo(14);
            try (var context = application(database.url(), false, true)) {
                WorkspaceRepository workspaces = context.getBean(WorkspaceRepository.class);
                Workspace workspaceA = workspaces.findByPublicSlugAndStatus(
                        "leadflow-ai", WorkspaceStatus.ACTIVE).orElseThrow();
                Workspace workspaceB = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "workspace-b-leads", "Workspace B Leads", WorkspaceStatus.ACTIVE));
                UserRepository users = context.getBean(UserRepository.class);
                users.saveAndFlush(User.createAdministrator(workspaceA,
                        "lead-admin-a@example.invalid", "Lead Admin A", "{test}hash-a"));
                users.saveAndFlush(User.createAdministrator(workspaceB,
                        "lead-admin-b@example.invalid", "Lead Admin B", "{test}hash-b"));

                ServiceOfferingService services = context.getBean(ServiceOfferingService.class);
                LeadService leads = context.getBean(LeadService.class);
                authenticate(workspaceA);
                UUID serviceA = services.create(new CreateServiceRequest("Shared Consulting", "A service")).id();
                var leadA = leads.create(leadRequest("Shared Search", "shared@example.invalid", serviceA));
                var secondA = leads.create(leadRequest("Alpha Only", "alpha@example.invalid", serviceA));

                authenticate(workspaceB);
                UUID serviceB = services.create(new CreateServiceRequest("Shared Consulting", "B service")).id();
                var leadB = leads.create(leadRequest("Shared Search", "shared@example.invalid", serviceB));

                authenticate(workspaceA);
                assertThat(leads.findAll(null, null, null, PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
                assertThat(leads.findAll(null, null, "shared search", PageRequest.of(0, 20)).getContent())
                        .extracting("id").containsExactly(leadA.id());
                assertThat(leads.findAll(LeadStatus.QUALIFYING, null, null, PageRequest.of(0, 20)).getTotalElements())
                        .isEqualTo(2);
                assertThat(leads.findAll(null, QualificationState.PROCESSING, null,
                        PageRequest.of(0, 20)).getTotalElements()).isEqualTo(2);
                assertThat(leads.findById(leadA.id()).id()).isEqualTo(leadA.id());
                assertThatThrownBy(() -> leads.findById(leadB.id())).isInstanceOf(NotFoundException.class);
                assertThatThrownBy(() -> leads.changeStatus(leadB.id(), LeadStatus.QUALIFYING, leadB.version()))
                        .isInstanceOf(NotFoundException.class);
                assertThatThrownBy(() -> leads.create(leadRequest(
                        "Foreign Service", "foreign-service@example.invalid", serviceB)))
                        .isInstanceOf(NotFoundException.class);
                assertThatThrownBy(() -> leads.create(leadRequest(
                        "Duplicate A", " SHARED@EXAMPLE.INVALID ", serviceA)))
                        .isInstanceOf(DuplicateLeadException.class);
                Workspace suspendedAutomation = workspaces.saveAndFlush(Workspace.create(UUID.randomUUID(),
                        "suspended-automation", "Suspended Automation", WorkspaceStatus.SUSPENDED));
                var suspendedLead = workerLead(context.getBean(LeadRepository.class),
                        suspendedAutomation, "suspended-automation@example.invalid");
                assertThatThrownBy(() -> leads.qualify(suspendedLead.getId(),
                        new com.mohammadmurrar.leadflow.lead.api.QualificationRequest(
                                80, com.mohammadmurrar.leadflow.lead.LeadPriority.HIGH,
                                "Audit", "Safe summary", "Safe reply")))
                        .isInstanceOf(NotFoundException.class).hasMessage("Lead not found");

                DashboardService dashboard = context.getBean(DashboardService.class);
                AnalyticsService analytics = context.getBean(AnalyticsService.class);
                assertThat(dashboard.getStats("all").totalLeads()).isEqualTo(2);
                assertThat(analytics.getAnalytics("all").totalLeads()).isEqualTo(2);

                NotificationRepository notificationRepository = context.getBean(NotificationRepository.class);
                NotificationService notificationService = context.getBean(NotificationService.class);
                var notificationsA = notificationRepository.findAllByWorkspaceId(
                        workspaceA.getId(), PageRequest.of(0, 20));
                var notificationsB = notificationRepository.findAllByWorkspaceId(
                        workspaceB.getId(), PageRequest.of(0, 20));
                assertThat(notificationService.findAll(PageRequest.of(0, 1)).getTotalElements()).isEqualTo(2);
                assertThat(notificationService.getUnreadCount()).isEqualTo(2);
                UUID notificationA = notificationsA.getContent().getFirst().getId();
                UUID notificationB = notificationsB.getContent().getFirst().getId();
                notificationService.markAsRead(notificationA);
                assertThatThrownBy(() -> notificationService.markAsRead(notificationB))
                        .isInstanceOf(NotFoundException.class).hasMessage("Notification not found");
                assertThatThrownBy(() -> notificationService.markAsRead(UUID.randomUUID()))
                        .isInstanceOf(NotFoundException.class).hasMessage("Notification not found");
                notificationService.markAllAsRead();
                assertThat(notificationRepository.countUnreadByWorkspaceId(workspaceA.getId())).isZero();
                assertThat(notificationRepository.countUnreadByWorkspaceId(workspaceB.getId())).isOne();

                QualificationAttemptRepository attempts = context.getBean(QualificationAttemptRepository.class);
                QualificationAttemptService qualification = context.getBean(QualificationAttemptService.class);
                var attemptA = attempts.findLatestByLeadAndWorkspace(
                        secondA.id(), workspaceA.getId()).orElseThrow();
                qualification.start(secondA.id(), attemptA.getId(), new QualificationStartRequest("mysql-a"));
                qualification.fail(secondA.id(), attemptA.getId(), new QualificationFailureRequest(
                        QualificationFailureCode.UNKNOWN, "ignored", "mysql-a"));
                long failedVersion = context.getBean(LeadRepository.class).findById(secondA.id()).orElseThrow().getVersion();
                assertThat(qualification.history(secondA.id())).hasSize(1);
                assertThatThrownBy(() -> qualification.history(leadB.id())).isInstanceOf(NotFoundException.class);
                assertThat(qualification.retry(secondA.id(), failedVersion).attempt().attemptNumber()).isEqualTo(2);
                assertThatThrownBy(() -> qualification.retry(leadB.id(), leadB.version()))
                        .isInstanceOf(NotFoundException.class);

                authenticate(workspaceB);
                assertThat(leads.findAll(null, null, null, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
                assertThat(dashboard.getStats("all").totalLeads()).isEqualTo(1);
                assertThat(analytics.getAnalytics("all").totalLeads()).isEqualTo(1);
                assertThat(notificationService.findAll(PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
                assertThat(notificationService.getUnreadCount()).isOne();

                try (Connection connection = database.connection()) {
                    assertStep7D2BDatabaseAudit(connection);
                    assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE id = UUID_TO_BIN('"
                            + leadB.id() + "') AND status <> 'QUALIFYING'")).isZero();
                }
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private CreateLeadRequest leadRequest(String name, String email, UUID serviceId) {
        return new CreateLeadRequest(name, email, null, "Shared Company", serviceId, null,
                null, java.time.LocalDate.now().plusDays(7),
                "A sufficiently detailed disposable MySQL tenant-isolation request.", "audit");
    }

    private void assertStep7D2BDatabaseAudit(Connection connection) throws SQLException {
        assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE workspace_id IS NULL")).isZero();
        assertThat(count(connection, "SELECT (SELECT COUNT(*) FROM notifications WHERE workspace_id IS NULL) "
                + "+ (SELECT COUNT(*) FROM qualification_attempts WHERE workspace_id IS NULL) "
                + "+ (SELECT COUNT(*) FROM qualification_dispatch_outbox WHERE workspace_id IS NULL) "
                + "+ (SELECT COUNT(*) FROM email_outbox WHERE workspace_id IS NULL)")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM leads owned LEFT JOIN workspaces workspace "
                + "ON workspace.id = owned.workspace_id WHERE workspace.id IS NULL")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM leads lead_record JOIN services service "
                + "ON service.id = lead_record.service_id WHERE lead_record.workspace_id <> service.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM notifications child JOIN leads parent "
                + "ON parent.id = child.lead_id WHERE child.workspace_id <> parent.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_attempts child JOIN leads parent "
                + "ON parent.id = child.lead_id WHERE child.workspace_id <> parent.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_dispatch_outbox child "
                + "JOIN qualification_attempts parent ON parent.id = child.attempt_id "
                + "WHERE child.workspace_id <> parent.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox child JOIN leads parent "
                + "ON parent.id = child.lead_id WHERE child.workspace_id <> parent.workspace_id")).isZero();
    }

    @Test
    void invalidV12SettingsOwnershipFailsBeforeConstraintReplacement() throws Exception {
        try (Database database = newDatabase()) {
            Flyway throughV12 = Flyway.configure().dataSource(database.url(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("12"))
                    .load();
            assertThat(throughV12.migrate().migrationsExecuted).isEqualTo(12);
            try (Connection connection = database.connection()) {
                execute(connection, "UPDATE workspace_settings SET workspace_id = NULL");
            }

            assertThatThrownBy(() -> flyway(database.url()).migrate())
                    .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            try (Connection connection = database.connection()) {
                assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workspace_settings' "
                        + "AND CONSTRAINT_NAME = 'uk_workspace_settings_singleton'"))
                        .isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workspace_settings' "
                        + "AND CONSTRAINT_NAME = 'uk_workspace_settings_workspace'"))
                        .isZero();
            }
        }
    }

    @Test
    void invalidV13ServiceOwnershipFailsBeforeConstraintReplacement() throws Exception {
        try (Database database = newDatabase()) {
            Flyway throughV13 = Flyway.configure().dataSource(database.url(), USERNAME, PASSWORD)
                    .locations("classpath:db/migration")
                    .target(MigrationVersion.fromVersion("13"))
                    .load();
            assertThat(throughV13.migrate().migrationsExecuted).isEqualTo(13);
            try (Connection connection = database.connection()) {
                execute(connection, "INSERT INTO services "
                        + "(id, version, workspace_id, name, normalized_name, active, created_at, updated_at) "
                        + "VALUES (UUID_TO_BIN(UUID()), 0, NULL, 'Unowned', 'unowned', TRUE, "
                        + "CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
            }

            assertThatThrownBy(() -> flyway(database.url()).migrate())
                    .isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            try (Connection connection = database.connection()) {
                assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                        + "AND CONSTRAINT_NAME = 'uk_services_normalized_name'"))
                        .isEqualTo(1);
                assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                        + "AND CONSTRAINT_NAME = 'uk_services_workspace_normalized_name'"))
                        .isZero();
            }
        }
    }

    private Database newDatabase() throws Exception {
        assumeTrue(BASE_URL != null && !BASE_URL.isBlank()
                        && USERNAME != null && PASSWORD != null,
                "MySQL migration test environment is not configured");
        String name = "leadflow_v12_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(BASE_URL, USERNAME, PASSWORD);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE `" + name
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        return new Database(name, databaseUrl(name));
    }

    private String databaseUrl(String name) {
        int query = BASE_URL.indexOf('?');
        String prefix = query < 0 ? BASE_URL : BASE_URL.substring(0, query);
        String suffix = query < 0 ? "" : BASE_URL.substring(query);
        return (prefix.endsWith("/") ? prefix : prefix + "/") + name + suffix;
    }

    private Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, USERNAME, PASSWORD)
                .locations("classpath:db/migration").load();
    }

    private void assertApplicationStartsAgainstV14(String url) {
        try (var context = application(url, false)) {
            assertThat(context.isActive()).isTrue();
        }
    }

    private void assertPerWorkspaceSettingsSchema(Connection connection) throws SQLException {
        assertThat(text(connection, "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workspace_settings' "
                + "AND COLUMN_NAME = 'workspace_id'")).isEqualTo("NO");
        assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workspace_settings' "
                + "AND CONSTRAINT_NAME = 'uk_workspace_settings_workspace' "
                + "AND CONSTRAINT_TYPE = 'UNIQUE'"))
                .isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'workspace_settings' "
                + "AND CONSTRAINT_NAME IN ('uk_workspace_settings_singleton', "
                + "'chk_workspace_settings_singleton')")).isZero();
    }

    private void assertPerWorkspaceSettingsConstraints(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO workspaces "
                + "(id, version, public_slug, display_name, status, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-00000000b001'), 0, "
                + "'workspace-b-v13', 'Workspace B', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO workspace_settings "
                + "(id, version, singleton_key, workspace_id, workspace_name, time_zone, currency, "
                + "response_time_text, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-00000000b101'), 0, 1, "
                + "UUID_TO_BIN('00000000-0000-0000-0000-00000000b001'), 'Workspace B', 'UTC', 'USD', "
                + "'Response time', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        assertThat(count(connection, "SELECT COUNT(*) FROM workspace_settings WHERE singleton_key = 1"))
                .isEqualTo(2);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO workspace_settings "
                + "(id, version, singleton_key, workspace_id, workspace_name, time_zone, currency, "
                + "response_time_text, created_at, updated_at) SELECT UUID_TO_BIN(UUID()), 0, 1, workspace_id, "
                + "'Duplicate', 'UTC', 'USD', 'Response time', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) "
                + "FROM workspace_settings WHERE workspace_name = 'Workspace B'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO workspace_settings "
                + "(id, version, singleton_key, workspace_id, workspace_name, time_zone, currency, "
                + "response_time_text, created_at, updated_at) VALUES (UUID_TO_BIN(UUID()), 0, 1, NULL, "
                + "'Null', 'UTC', 'USD', 'Response time', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO workspace_settings "
                + "(id, version, singleton_key, workspace_id, workspace_name, time_zone, currency, "
                + "response_time_text, created_at, updated_at) VALUES (UUID_TO_BIN(UUID()), 0, 1, "
                + "UUID_TO_BIN('ffffffff-ffff-ffff-ffff-ffffffffffff'), 'Unknown', 'UTC', 'USD', "
                + "'Response time', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);
    }

    private void assertPerWorkspaceServiceSchema(Connection connection) throws SQLException {
        assertThat(text(connection, "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                + "AND COLUMN_NAME = 'workspace_id'")).isEqualTo("NO");
        assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                + "AND CONSTRAINT_NAME = 'uk_services_workspace_normalized_name' "
                + "AND CONSTRAINT_TYPE = 'UNIQUE'")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                + "AND CONSTRAINT_NAME = 'uk_services_normalized_name'")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'services' "
                + "AND CONSTRAINT_NAME = 'fk_services_workspace' "
                + "AND CONSTRAINT_TYPE = 'FOREIGN KEY'")).isEqualTo(1);
    }

    private void assertPerWorkspaceServiceConstraints(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO services "
                + "(id, version, workspace_id, name, normalized_name, active, created_at, updated_at) "
                + "SELECT UUID_TO_BIN('00000000-0000-0000-0000-00000000b201'), 0, workspace_id, "
                + "'Consulting', 'consulting', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) "
                + "FROM workspace_settings WHERE workspace_name = 'Workspace B'");
        execute(connection, "INSERT INTO services "
                + "(id, version, workspace_id, name, normalized_name, active, created_at, updated_at) "
                + "SELECT UUID_TO_BIN('00000000-0000-0000-0000-00000000a201'), 0, workspace_id, "
                + "'Consulting', 'consulting', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) "
                + "FROM workspace_settings WHERE workspace_name <> 'Workspace B'");
        assertThat(count(connection, "SELECT COUNT(*) FROM services WHERE normalized_name = 'consulting'"))
                .isEqualTo(2);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO services "
                + "(id, version, workspace_id, name, normalized_name, active, created_at, updated_at) "
                + "SELECT UUID_TO_BIN(UUID()), 0, workspace_id, 'Duplicate', 'consulting', TRUE, "
                + "CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6) FROM workspace_settings "
                + "WHERE workspace_name = 'Workspace B'"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute(connection, "INSERT INTO services "
                + "(id, version, workspace_id, name, normalized_name, active, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN(UUID()), 0, NULL, 'Null', 'null', TRUE, CURRENT_TIMESTAMP(6), "
                + "CURRENT_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class);
    }

    private org.springframework.context.ConfigurableApplicationContext application(
            String url, boolean enablePasswordReset) {
        return application(url, enablePasswordReset, false);
    }

    private org.springframework.context.ConfigurableApplicationContext application(
            String url, boolean enablePasswordReset, boolean enableQualificationRetry) {
        return new SpringApplicationBuilder(LeadFlowApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--spring.datasource.url=" + url,
                        "--spring.datasource.username=" + USERNAME,
                        "--spring.datasource.password=" + PASSWORD,
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--leadflow.bootstrap.enabled=false",
                        "--leadflow.email-delivery.enabled=false",
                        "--leadflow.password-reset.enabled=" + enablePasswordReset,
                        "--leadflow.password-reset.active-key-version=v1",
                        "--leadflow.password-reset.hmac-keys=v1=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "--leadflow.qualification-reliability.dispatcher-enabled=false",
                        "--leadflow.qualification-reliability.retry-enabled=" + enableQualificationRetry);
    }

    private void authenticate(Workspace workspace) {
        var principal = new AuthenticatedPrincipal(UUID.randomUUID(),
                "audit-admin@example.invalid", "Audit Administrator", UserRole.ADMIN,
                workspace.getId(), "{test}audit-hash", true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.getAuthorities()));
    }

    private void assertCreationPathOwnership(Connection connection, UUID workspaceA,
            UUID workspaceB) throws SQLException {
        assertOwnershipComplete(connection);
        assertParentOwnershipMatches(connection);
        assertThat(count(connection, "SELECT COUNT(*) FROM services WHERE name = 'Workspace A Service' "
                + "AND workspace_id = UUID_TO_BIN('" + workspaceA + "')")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM services WHERE name = 'Workspace B Service' "
                + "AND workspace_id = UUID_TO_BIN('" + workspaceB + "')")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE workspace_id = UUID_TO_BIN('"
                + workspaceA + "')")).isEqualTo(2);
        assertThat(count(connection, "SELECT COUNT(*) FROM leads WHERE workspace_id = UUID_TO_BIN('"
                + workspaceB + "')")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM workspace_settings")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM users")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM services")).isEqualTo(2);
        assertThat(count(connection, "SELECT COUNT(*) FROM notifications")).isEqualTo(2);
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_attempts")).isEqualTo(2);
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_dispatch_outbox")).isEqualTo(2);
        assertThat(count(connection, "SELECT COUNT(*) FROM password_reset_requests")).isEqualTo(1);
        assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox")).isEqualTo(3);
        assertThat(count(connection, "SELECT COUNT(*) FROM workspace_notification_recipients")).isEqualTo(1);
        for (String table : new String[] {"leads", "notifications", "qualification_attempts",
                "qualification_dispatch_outbox", "email_outbox", "password_reset_requests",
                "workspace_notification_recipients"}) {
            assertThat(count(connection, "SELECT COUNT(*) FROM " + table
                    + " WHERE workspace_id <> UUID_TO_BIN('" + workspaceA + "')"))
                    .as(table).isZero();
        }
    }

    private void assertLegacyWorkspace(Connection connection) throws SQLException {
        assertThat(count(connection, "SELECT COUNT(*) FROM workspaces")).isEqualTo(1);
        assertThat(text(connection, "SELECT public_slug FROM workspaces")).isEqualTo("leadflow-ai");
        assertThat(text(connection, "SELECT status FROM workspaces")).isEqualTo("ACTIVE");
        assertThat(count(connection, "SELECT COUNT(*) FROM workspaces workspace "
                + "JOIN workspace_settings settings ON settings.id = workspace.id "
                + "AND settings.workspace_id = workspace.id")).isEqualTo(1);
    }

    private void assertOwnershipComplete(Connection connection) throws SQLException {
        for (String table : OWNED_TABLES) {
            assertThat(count(connection, "SELECT COUNT(*) FROM " + table
                    + " WHERE workspace_id IS NULL")).as(table).isZero();
            assertThat(count(connection, "SELECT COUNT(*) FROM " + table + " owned LEFT JOIN workspaces "
                    + "workspace ON workspace.id = owned.workspace_id WHERE workspace.id IS NULL"))
                    .as(table).isZero();
        }
    }

    private void assertParentOwnershipMatches(Connection connection) throws SQLException {
        assertThat(count(connection, "SELECT COUNT(*) FROM workspace_notification_recipients recipient "
                + "JOIN workspace_settings settings ON settings.id = recipient.workspace_settings_id "
                + "WHERE recipient.workspace_id <> settings.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM notifications notification JOIN leads lead_record "
                + "ON lead_record.id = notification.lead_id WHERE notification.workspace_id <> lead_record.workspace_id"))
                .isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_attempts attempt JOIN leads lead_record "
                + "ON lead_record.id = attempt.lead_id WHERE attempt.workspace_id <> lead_record.workspace_id"))
                .isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM qualification_dispatch_outbox dispatch "
                + "JOIN qualification_attempts attempt ON attempt.id = dispatch.attempt_id "
                + "WHERE dispatch.workspace_id <> attempt.workspace_id")).isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM password_reset_requests reset_request JOIN users user_account "
                + "ON user_account.id = reset_request.user_id WHERE reset_request.workspace_id <> user_account.workspace_id"))
                .isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox outbox JOIN leads lead_record "
                + "ON lead_record.id = outbox.lead_id WHERE outbox.workspace_id <> lead_record.workspace_id"))
                .isZero();
        assertThat(count(connection, "SELECT COUNT(*) FROM email_outbox outbox "
                + "JOIN password_reset_requests reset_request ON reset_request.id = outbox.password_reset_request_id "
                + "WHERE outbox.workspace_id <> reset_request.workspace_id")).isZero();
    }

    private void assertForeignKeysRejectUnknownWorkspace(Connection connection) {
        assertThatThrownBy(() -> execute(connection, "INSERT INTO services "
                + "(id, version, name, normalized_name, active, created_at, updated_at, workspace_id) VALUES "
                + "(UUID_TO_BIN(UUID()), 0, 'Invalid', 'invalid', TRUE, CURRENT_TIMESTAMP(6), "
                + "CURRENT_TIMESTAMP(6), UUID_TO_BIN('ffffffff-ffff-ffff-ffff-ffffffffffff'))"))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).startsWith("23"));
    }

    private void assertDuplicateSlugRejected(Connection connection) {
        assertThatThrownBy(() -> execute(connection, "INSERT INTO workspaces "
                + "(id, version, public_slug, display_name, status, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN(UUID()), 0, 'leadflow-ai', 'Duplicate', 'ACTIVE', "
                + "CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))"))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).startsWith("23"));
    }

    private void assertWorkspaceDeleteRestricted(Connection connection) {
        assertThatThrownBy(() -> execute(connection, "DELETE FROM workspaces WHERE public_slug = 'leadflow-ai'"))
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).startsWith("23"));
    }

    private void insertLegacyFixture(Connection connection) throws SQLException {
        execute(connection, "UPDATE workspace_settings SET workspace_name = 'Legacy Workspace'");
        execute(connection, "INSERT INTO workspace_notification_recipients "
                + "(id, workspace_settings_id, normalized_email, created_at) "
                + "SELECT UUID_TO_BIN('00000000-0000-0000-0000-000000000101'), id, "
                + "'notify@example.invalid', CURRENT_TIMESTAMP(6) FROM workspace_settings");
        execute(connection, "INSERT INTO users (id, version, email, normalized_email, password_hash, "
                + "display_name, role, enabled, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000201'), 0, "
                + "'legacy-admin@example.invalid', 'legacy-admin@example.invalid', 'hash', "
                + "'Legacy Admin', 'ADMIN', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO services (id, version, name, normalized_name, active, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000211'), 0, 'Service One', 'service one', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),"
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000212'), 0, 'Service Two', 'service two', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO leads (id, version, full_name, email, requested_service, service_id, "
                + "message, source, status, priority, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000301'), 0, 'Synthetic Lead', "
                + "'synthetic@example.invalid', 'Service One', UUID_TO_BIN('00000000-0000-0000-0000-000000000211'), "
                + "'Synthetic migration fixture message.', 'test', 'QUALIFYING', 'UNASSESSED', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO notifications (id, version, type, severity, title, message, lead_id, created_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000401'), 0, 'NEW_LEAD', 'INFO', "
                + "'Fixture', 'Fixture notification', UUID_TO_BIN('00000000-0000-0000-0000-000000000301'), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO qualification_attempts (id, version, lead_id, attempt_number, status, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000501'), 0, UUID_TO_BIN('00000000-0000-0000-0000-000000000301'), "
                + "1, 'PENDING', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO qualification_dispatch_outbox (id, version, attempt_id, status, delivery_count, available_at, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000601'), 0, UUID_TO_BIN('00000000-0000-0000-0000-000000000501'), "
                + "'PENDING', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
        execute(connection, "INSERT INTO password_reset_requests (id, version, user_id, token_hash, active_slot, created_at, expires_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000801'), 0, UUID_TO_BIN('00000000-0000-0000-0000-000000000201'), "
                + "UNHEX(REPEAT('11', 32)), 1, CURRENT_TIMESTAMP(6), TIMESTAMPADD(HOUR, 1, CURRENT_TIMESTAMP(6)))");
        execute(connection, "INSERT INTO email_outbox (id, version, template_type, recipient, lead_id, deduplication_key, status, delivery_count, available_at, delivered_at, created_at, updated_at) VALUES "
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000701'), 0, 'NEW_INQUIRY', 'notify@example.invalid', UUID_TO_BIN('00000000-0000-0000-0000-000000000301'), "
                + "'fixture:linked', 'DELIVERED', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)),"
                + "(UUID_TO_BIN('00000000-0000-0000-0000-000000000702'), 0, 'NEW_INQUIRY', 'notify@example.invalid', NULL, "
                + "'fixture:detached', 'DELIVERED', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))");
    }

    private Map<String, Long> counts(Connection connection) throws SQLException {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String table : OWNED_TABLES) result.put(table, count(connection, "SELECT COUNT(*) FROM " + table));
        return result;
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private String text(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private final class Database implements AutoCloseable {
        private final String name;
        private final String url;

        private Database(String name, String url) {
            this.name = name;
            this.url = url;
        }

        private String url() { return url; }
        private Connection connection() throws SQLException {
            return DriverManager.getConnection(url, USERNAME, PASSWORD);
        }

        @Override
        public void close() throws Exception {
            try (Connection connection = DriverManager.getConnection(BASE_URL, USERNAME, PASSWORD);
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS `" + name + "`");
            }
        }
    }
}
