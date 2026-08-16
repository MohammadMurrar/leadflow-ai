package com.mohammadmurrar.leadflow.bootstrap;

import com.mohammadmurrar.leadflow.user.User;
import com.mohammadmurrar.leadflow.user.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@ConditionalOnProperty(name = "leadflow.bootstrap.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final AdminBootstrapProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final ConfigurableApplicationContext context;
    private final TransactionTemplate transactionTemplate;

    public AdminBootstrapRunner(AdminBootstrapProperties properties, UserRepository users,
            PasswordEncoder passwordEncoder, JdbcTemplate jdbcTemplate, Environment environment,
            ConfigurableApplicationContext context, PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.context = context;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        validateSafeMode();
        String normalizedEmail = User.normalizeEmail(properties.email());
        validatePassword(properties.password());
        if (properties.displayName() == null || properties.displayName().isBlank()) {
            throw new IllegalStateException("Bootstrap display name is required");
        }

        transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.queryForObject(
                    "SELECT singleton_key FROM workspace_settings WHERE singleton_key = 1 FOR UPDATE",
                    Byte.class);
            if (users.count() > 0) {
                if (users.findByNormalizedEmail(normalizedEmail).isPresent()) return;
                throw new IllegalStateException("Administrator bootstrap refused because a user already exists");
            }
            users.saveAndFlush(User.createAdministrator(properties.email(), properties.displayName(),
                    passwordEncoder.encode(properties.password())));
        });
        context.close();
    }

    private void validateSafeMode() {
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""))) {
            throw new IllegalStateException("Administrator bootstrap requires a non-web application");
        }
        if (environment.getProperty("leadflow.qualification-reliability.dispatcher-enabled",
                Boolean.class, false)) {
            throw new IllegalStateException("Administrator bootstrap requires automation dispatch to be disabled");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 256) {
            throw new IllegalStateException("Bootstrap password must contain between 12 and 256 characters");
        }
    }
}
