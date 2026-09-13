package com.mohammadmurrar.leadflow.security;

import com.mohammadmurrar.leadflow.common.ConflictException;
import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.email.EmailOutboxRepository;
import com.mohammadmurrar.leadflow.lead.*;
import com.mohammadmurrar.leadflow.passwordreset.*;
import com.mohammadmurrar.leadflow.qualification.*;
import com.mohammadmurrar.leadflow.qualification.api.*;
import com.mohammadmurrar.leadflow.user.*;
import com.mohammadmurrar.leadflow.workspace.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringBootTest(properties = {
        "leadflow.email-delivery.enabled=false", "leadflow.qualification-reliability.dispatcher-enabled=false",
        "leadflow.qualification-reliability.dispatcher-interval=PT24H",
        "leadflow.qualification-reliability.timeout-interval=PT24H",
        "leadflow.admin-bootstrap.enabled=false", "leadflow.provisioning.enabled=false"
})
@DirtiesContext
class SecurityCorrectionIntegrationTest {
    private static final String NEW_PASSWORD = "Aa1!" + UUID.randomUUID();
    private static final String KEY = "v1=" + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(new java.security.SecureRandom().generateSeed(32));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = System.getenv("LEADFLOW_CORRECTION_MYSQL_URL");
        boolean mysql = url != null;
        if (mysql && (!url.startsWith("jdbc:mysql://127.0.0.1:") || url.contains(":3307/"))) {
            throw new IllegalStateException("Disposable loopback database required");
        }
        registry.add("spring.datasource.url", () -> mysql ? url : "jdbc:h2:mem:security_correction;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        registry.add("spring.datasource.username", () -> mysql ? "correction" : "sa");
        registry.add("spring.datasource.password", () -> mysql ? System.getenv("LEADFLOW_CORRECTION_MYSQL_PASSWORD") : "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> mysql ? "validate" : "create-drop");
        registry.add("spring.flyway.enabled", () -> mysql);
        registry.add("spring.session.jdbc.initialize-schema", () -> mysql ? "never" : "always");
        registry.add("leadflow.password-reset.enabled", () -> true);
        registry.add("leadflow.password-reset.active-key-version", () -> "v1");
        registry.add("leadflow.password-reset.hmac-keys", () -> KEY);
        registry.add("logging.level.org.springframework.test.web.servlet", () -> "OFF");
    }

    @Autowired WorkspaceRepository workspaces;
    @Autowired UserRepository users;
    @Autowired PasswordResetRequestRepository resets;
    @Autowired PasswordResetRequestService requestReset;
    @Autowired PasswordResetConfirmationService confirmReset;
    @Autowired PasswordResetTokenService tokens;
    @Autowired PasswordResetEmailDeliveryService resetDelivery;
    @Autowired EmailOutboxRepository emails;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcIndexedSessionRepository sessions;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired WebApplicationContext context;
    @Autowired ObjectProvider<Flyway> flyway;
    @Autowired QualificationAttemptService qualification;
    @Autowired LeadRepository leads;
    @Autowired QualificationAttemptRepository attempts;
    @Autowired QualificationDispatchOutboxRepository outboxes;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean IdentityStateService identities;
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = webAppContextSetup(context)
                .addFilters(context.getBean("springSessionRepositoryFilter", jakarta.servlet.Filter.class))
                .apply(springSecurity()).build();
    }
    private <T> T tx(Supplier<T> work) { return new TransactionTemplate(transactions).execute(status -> work.get()); }
    private void change(Runnable work) { tx(() -> { work.run(); return null; }); }

    private User identity(WorkspaceStatus status) {
        return tx(() -> {
            UUID id = UUID.randomUUID();
            Workspace workspace = workspaces.saveAndFlush(Workspace.create(id, "check-" + id, "Synthetic", status));
            return users.saveAndFlush(User.createAdministrator(workspace, id + "@example.invalid", "Synthetic",
                    encoder.encode("Aa1!" + UUID.randomUUID())));
        });
    }
    private void state(User user, String state) {
        change(() -> {
            User current = users.findById(user.getId()).orElseThrow();
            switch (state) {
                case "disabled" -> ReflectionTestUtils.setField(current, "enabled", false);
                case "null" -> ReflectionTestUtils.setField(current, "workspace", null);
                case "foreign" -> ReflectionTestUtils.setField(current, "workspace", workspaces.saveAndFlush(
                        Workspace.create(UUID.randomUUID(), "foreign-" + UUID.randomUUID(), "Foreign", WorkspaceStatus.ACTIVE)));
                default -> ReflectionTestUtils.setField(org.hibernate.Hibernate.unproxy(current.getWorkspace()),
                        "status", WorkspaceStatus.valueOf(state));
            }
            entities.flush();
        });
    }
    private UUID issue(User user) {
        assertThat(requestReset.request(user.getNormalizedEmail(), Instant.now())).isEqualTo(PasswordResetRequestService.Result.CREATED);
        return tx(() -> resets.findActiveByUserIdForUpdate(user.getId()).orElseThrow().getId());
    }
    private String token(UUID id) {
        return tx(() -> {
            var request = resets.findById(id).orElseThrow();
            byte[] nonce = request.getDeliveryNonce();
            try (var sensitive = tokens.derive(id, request.getUser().getId(), request.getExpiresAt(), nonce, request.getDeliveryKeyVersion())) {
                return sensitive.encoded();
            } finally { Arrays.fill(nonce, (byte) 0); }
        });
    }
    private String passwordHash(User user) { return tx(() -> users.findById(user.getId()).orElseThrow().getPasswordHash()); }
    private String session(User user) {
        var principal = new AuthenticatedPrincipal(user.getId(), user.getNormalizedEmail(), user.getDisplayName(),
                user.getRole(), user.getWorkspace().getId(), null, true);
        var security = SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        var session = sessions.createSession();
        org.springframework.session.Session view = session;
        view.setAttribute("SPRING_SECURITY_CONTEXT", security);
        sessions.save(session);
        return view.getId();
    }
    private Cookie cookie(String session) {
        return new Cookie("LEADFLOW_SESSION", Base64.getEncoder().encodeToString(session.getBytes(StandardCharsets.UTF_8)));
    }

    @Test void onlyProtectedAuthenticatedRequestsPerformFreshIdentityLookup() throws Exception {
        User user = identity(WorkspaceStatus.ACTIVE);
        String session = session(user);
        org.mockito.Mockito.clearInvocations(identities);
        for (String path : List.of("/api/v1/auth/csrf", "/actuator/health/readiness",
                "/actuator/health/liveness", "/api/v1/public/workspaces/unknown/inquiry-config",
                "/api/v1/automation/unknown", "/unmapped-static-resource")) {
            mvc.perform(get(path).cookie(cookie(session)));
        }
        mvc.perform(post("/api/v1/auth/password-reset/request").cookie(cookie(session)).with(csrf())
                .contentType("application/json").content("{\"email\":\"unknown@example.invalid\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verify(identities, org.mockito.Mockito.never()).isActive(org.mockito.ArgumentMatchers.any());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(session))).andExpect(status().isOk());
        org.mockito.Mockito.verify(identities).isActive(org.mockito.ArgumentMatchers.any());
        var filters = context.getBean(org.springframework.security.web.FilterChainProxy.class)
                .getFilters("/api/v1/auth/me").stream().map(Object::getClass).toList();
        assertThat(filters.indexOf(ActiveIdentityFilter.class)).isGreaterThan(
                filters.indexOf(org.springframework.security.web.context.SecurityContextHolderFilter.class));
        assertThat(filters.indexOf(ActiveIdentityFilter.class)).isLessThan(
                filters.indexOf(org.springframework.security.web.access.intercept.AuthorizationFilter.class));
    }

    @Test void freshAndRepeatedMigrationsValidateWhenUsingDisposableMysql() {
        Flyway database = flyway.getIfAvailable();
        if (database == null) return;
        assertThat(database.info().applied()).hasSize(14);
        assertThat(Arrays.stream(database.info().applied()).filter(info -> "14".equals(info.getVersion().toString())).count()).isOne();
        assertThat(database.migrate().migrationsExecuted).isZero();
        database.validate();
    }

    @Test void resetRequestsAndAcknowledgementsRemainSafeForInactiveAndUnknownIdentities() throws Exception {
        User active = identity(WorkspaceStatus.ACTIVE);
        long before = resets.count();
        long emailBefore = emails.count();
        issue(active);
        assertThat(resets.count()).isEqualTo(before + 1);
        assertThat(emails.count()).isEqualTo(emailBefore + 1);
        List<String> addresses = new ArrayList<>();
        addresses.add(UUID.randomUUID() + "@example.invalid");
        for (String status : List.of("disabled", "PENDING", "SUSPENDED", "null")) {
            User user = identity(WorkspaceStatus.ACTIVE);
            state(user, status);
            addresses.add(user.getNormalizedEmail());
        }
        long resetCount = resets.count(), emailCount = emails.count();
        for (String address : addresses) {
            assertThat(requestReset.request(address, Instant.now())).isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
            mvc.perform(post("/api/v1/auth/password-reset/request").with(csrf()).contentType("application/json")
                    .content("{\"email\":\"" + address + "\"}"))
                    .andExpect(status().isNoContent()).andExpect(content().string(""));
        }
        assertThat(resets.count()).isEqualTo(resetCount);
        assertThat(emails.count()).isEqualTo(emailCount);
    }

    @Test void inactiveOrReassignedTokensCannotChangePasswordsConsumeStateOrAffectAnotherTenant() {
        User foreign = identity(WorkspaceStatus.ACTIVE);
        UUID foreignRequest = issue(foreign);
        String foreignHash = passwordHash(foreign);
        for (String status : List.of("disabled", "PENDING", "SUSPENDED", "null", "foreign")) {
            User user = identity(WorkspaceStatus.ACTIVE);
            UUID request = issue(user);
            String encoded = token(request), oldHash = passwordHash(user);
            state(user, status);
            long emailCount = emails.count();
            long resetCount = resets.count();
            assertThat(requestReset.request(user.getNormalizedEmail(), Instant.now().plusSeconds(301)))
                    .isEqualTo(PasswordResetRequestService.Result.SUPPRESSED);
            assertThat(resets.count()).isEqualTo(resetCount);
            assertThatThrownBy(() -> confirmReset.confirm(encoded, NEW_PASSWORD))
                    .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class)
                    .hasMessage(PasswordResetConfirmationService.INVALID_TOKEN_MESSAGE);
            assertThat(Objects.equals(oldHash, passwordHash(user))).isTrue();
            assertThat(resets.findById(request).orElseThrow().getConsumedAt()).isNull();
            assertThat(emails.count()).isEqualTo(emailCount);
            assertThatThrownBy(() -> resetDelivery.prepare(request, user.getWorkspace().getId(), Instant.now()))
                    .isInstanceOf(PasswordResetEmailDeliveryService.PasswordResetEmailUnavailableException.class);
        }
        assertThat(Objects.equals(foreignHash, passwordHash(foreign))).isTrue();
        assertThat(resets.findById(foreignRequest).orElseThrow().getConsumedAt()).isNull();
    }

    @Test void activeConfirmationIsSingleUseAndRevokesOnlyItsOwnSessions() throws Exception {
        User a = identity(WorkspaceStatus.ACTIVE), b = identity(WorkspaceStatus.ACTIVE);
        String sessionA = session(a), sessionB = session(b);
        UUID request = issue(a);
        String encoded = token(request);
        confirmReset.confirm(encoded, NEW_PASSWORD);
        assertThat(encoder.matches(NEW_PASSWORD, passwordHash(a))).isTrue();
        assertThat(resets.findById(request).orElseThrow().getConsumedAt()).isNotNull();
        assertThat(sessions.findById(sessionA) == null).isTrue();
        assertThat(sessions.findById(sessionB) != null).isTrue();
        assertThatThrownBy(() -> confirmReset.confirm(encoded, NEW_PASSWORD))
                .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class);
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(sessionA))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/auth/me").cookie(cookie(sessionB))).andExpect(status().isOk());
    }

    @Test void nextRequestRevokesDisabledInactiveAndReassignedSessions() throws Exception {
        for (String status : List.of("disabled", "PENDING", "SUSPENDED", "null", "foreign")) {
            User user = identity(WorkspaceStatus.ACTIVE);
            String session = session(user);
            mvc.perform(get("/api/v1/auth/me").cookie(cookie(session))).andExpect(status().isOk());
            state(user, status);
            mvc.perform(get("/api/v1/auth/me").cookie(cookie(session)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Authentication is required"));
            assertThat(sessions.findById(session) == null).isTrue();
            mvc.perform(get("/api/v1/leads").cookie(cookie(session))).andExpect(status().isUnauthorized());
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"disabled", "PENDING", "SUSPENDED"})
    void committedStatusChangeWinsOverAnEarlierPersistenceContextSnapshot(String newState) throws Exception {
        User user = identity(WorkspaceStatus.ACTIVE);
        UUID requestId = issue(user);
        String encoded = token(requestId), oldHash = passwordHash(user);
        var snapshotRead = new CountDownLatch(1);
        var statusCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> confirmation = executor.submit(() -> tx(() -> {
                users.findById(user.getId()).orElseThrow().getWorkspace().getStatus();
                resets.findById(requestId).orElseThrow();
                snapshotRead.countDown();
                try { if (!statusCommitted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Status change timed out"); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
                assertThatThrownBy(() -> confirmReset.confirm(encoded, NEW_PASSWORD))
                        .isInstanceOf(PasswordResetConfirmationService.InvalidPasswordResetException.class);
                return null;
            }));
            assertThat(snapshotRead.await(10, TimeUnit.SECONDS)).isTrue();
            state(user, newState);
            statusCommitted.countDown();
            try { confirmation.get(15, TimeUnit.SECONDS); }
            catch (ExecutionException exception) {
                // The joined transactional rejection marks the outer test transaction rollback-only.
                if (!(exception.getCause() instanceof org.springframework.transaction.UnexpectedRollbackException)) throw exception;
            }
        } finally { statusCommitted.countDown(); executor.shutdownNow(); }
        assertThat(Objects.equals(oldHash, passwordHash(user))).isTrue();
        assertThat(resets.findById(requestId).orElseThrow().getConsumedAt()).isNull();
    }

    private record Attempt(UUID lead, UUID attempt, UUID workspace) {}
    private Attempt attempt(User user) {
        return tx(() -> {
            Workspace workspace = workspaces.findById(user.getWorkspace().getId()).orElseThrow();
            Lead lead = Lead.create(workspace, "Synthetic", UUID.randomUUID() + "@example.invalid", null,
                    "Synthetic", "Automation", null, BigDecimal.TEN, LocalDate.now().plusDays(10), "Synthetic inquiry only", "test");
            lead.startQualification();
            leads.saveAndFlush(lead);
            QualificationAttempt attempt = qualification.createInitialAttempt(lead);
            entities.flush();
            return new Attempt(lead.getId(), attempt.getId(), workspace.getId());
        });
    }
    private QualificationSuccessRequest success(String execution) {
        return new QualificationSuccessRequest(80, LeadPriority.HIGH, "AUTOMATION", "Synthetic result", "Synthetic reply", execution);
    }
    private void unchanged(Attempt attempt) {
        assertThat(attempts.findById(attempt.attempt()).orElseThrow().getStatus()).isEqualTo(QualificationAttemptStatus.PROCESSING);
        assertThat(leads.findById(attempt.lead()).orElseThrow().getStatus()).isEqualTo(LeadStatus.QUALIFYING);
    }

    @Test void callbacksBindExecutionAndOwnershipWithoutMutatingForeignOrMismatchedChains() {
        User a = identity(WorkspaceStatus.ACTIVE), b = identity(WorkspaceStatus.ACTIVE);
        Attempt first = attempt(a), foreign = attempt(b);
        qualification.start(first.lead(), first.attempt(), new QualificationStartRequest("accepted-A"));
        qualification.start(foreign.lead(), foreign.attempt(), new QualificationStartRequest("accepted-B"));
        for (String execution : new String[]{"accepted-B", null, ""}) {
            assertThatThrownBy(() -> qualification.succeed(first.lead(), first.attempt(), success(execution)))
                    .isInstanceOf(ConflictException.class);
            assertThatThrownBy(() -> qualification.fail(first.lead(), first.attempt(),
                    new QualificationFailureRequest(QualificationFailureCode.UNKNOWN, "Synthetic", execution)))
                    .isInstanceOf(ConflictException.class);
        }
        assertThatThrownBy(() -> qualification.succeed(foreign.lead(), first.attempt(), success("accepted-A")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> qualification.succeed(first.lead(), foreign.attempt(), success("accepted-A")))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> qualification.succeed(UUID.randomUUID(), UUID.randomUUID(), success("accepted-A")))
                .isInstanceOf(NotFoundException.class);
        unchanged(first); unchanged(foreign);
        qualification.succeed(first.lead(), first.attempt(), success("accepted-A"));
        qualification.succeed(first.lead(), first.attempt(), success("accepted-A"));
        assertThatThrownBy(() -> qualification.fail(first.lead(), first.attempt(),
                new QualificationFailureRequest(QualificationFailureCode.UNKNOWN, "Synthetic", "accepted-A")))
                .isInstanceOf(ConflictException.class);
        qualification.fail(foreign.lead(), foreign.attempt(),
                new QualificationFailureRequest(QualificationFailureCode.UNKNOWN, "Synthetic", "accepted-B"));
        qualification.fail(foreign.lead(), foreign.attempt(),
                new QualificationFailureRequest(QualificationFailureCode.UNKNOWN, "Synthetic", "accepted-B"));

        for (String invalid : List.of("outbox", "null", "PENDING", "SUSPENDED")) {
            User owner = identity(WorkspaceStatus.ACTIVE);
            Attempt attempt = attempt(owner);
            qualification.start(attempt.lead(), attempt.attempt(), new QualificationStartRequest("accepted"));
            if (invalid.equals("outbox")) change(() -> {
                var outbox = outboxes.findAll().stream().filter(item -> item.getAttempt().getId().equals(attempt.attempt())).findFirst().orElseThrow();
                ReflectionTestUtils.setField(outbox, "workspace", workspaces.findById(b.getWorkspace().getId()).orElseThrow());
                entities.flush();
            });
            else if (invalid.equals("null")) change(() -> {
                ReflectionTestUtils.setField(attempts.findById(attempt.attempt()).orElseThrow(), "workspace", null);
                entities.flush();
            });
            else state(owner, invalid);
            assertThatThrownBy(() -> qualification.succeed(attempt.lead(), attempt.attempt(), success("accepted")))
                    .isInstanceOf(NotFoundException.class);
            assertThat(qualification.timeOut(attempt.attempt())).isFalse();
            unchanged(attempt);
        }
    }
}
