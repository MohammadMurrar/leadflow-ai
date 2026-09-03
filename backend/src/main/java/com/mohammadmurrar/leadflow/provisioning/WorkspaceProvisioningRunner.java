package com.mohammadmurrar.leadflow.provisioning;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(name = "leadflow.provisioning.enabled", havingValue = "true")
public class WorkspaceProvisioningRunner implements ApplicationRunner {
    private final WorkspaceProvisioningService provisioning;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    public WorkspaceProvisioningRunner(WorkspaceProvisioningService provisioning,
            Environment environment, ConfigurableApplicationContext context) {
        this.provisioning = provisioning;
        this.environment = environment;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        char[] password = null;
        try {
            validateSafeMode();
            String secret = required("PROVISIONING_ADMIN_PASSWORD");
            password = secret.toCharArray();
            WorkspaceProvisioningCommand command = new WorkspaceProvisioningCommand(
                    required("PROVISIONING_WORKSPACE_NAME"), required("PROVISIONING_PUBLIC_SLUG"),
                    optional("PROVISIONING_PUBLIC_DESCRIPTION"), required("PROVISIONING_ADMIN_NAME"),
                    required("PROVISIONING_ADMIN_EMAIL"), password, parseServices(), parseRecipients());
            if (environment.getProperty("PROVISIONING_DRY_RUN", Boolean.class, false)) {
                WorkspaceProvisioningService.Preview preview = provisioning.preview(command);
                if (!preview.slugAvailable() || !preview.administratorEmailAvailable()) {
                    throw new IllegalStateException("Provisioning preview found an existing-data conflict");
                }
                System.out.println("Workspace provisioning preview passed; no data was written");
            } else {
                provisioning.provision(command);
                System.out.println("Workspace provisioning completed successfully");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Workspace provisioning failed", exception);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            context.close();
        }
    }

    private void validateSafeMode() {
        if (java.util.Arrays.stream(environment.getActiveProfiles())
                .noneMatch("provisioning"::equals)) {
            throw new IllegalStateException("Provisioning requires the dedicated provisioning profile");
        }
        if (!"none".equalsIgnoreCase(environment.getProperty("spring.main.web-application-type", ""))) {
            throw new IllegalStateException("Provisioning requires the dedicated non-web application");
        }
        if (environment.getProperty("leadflow.bootstrap.enabled", Boolean.class, false)
                || environment.getProperty("leadflow.email-delivery.enabled", Boolean.class, false)
                || environment.getProperty("leadflow.qualification-reliability.dispatcher-enabled", Boolean.class, false)
                || environment.getProperty("leadflow.qualification-reliability.retry-enabled", Boolean.class, false)
                || environment.getProperty("leadflow.qualification-reliability.legacy-callback-enabled", Boolean.class, false)) {
            throw new IllegalStateException("Provisioning requires all background and bootstrap modes disabled");
        }
    }

    private String required(String name) {
        String value = optional(name);
        if (value == null) throw new IllegalStateException("Required provisioning input is missing");
        return value;
    }

    private String optional(String name) {
        String value = environment.getProperty(name);
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> parseRecipients() {
        String value = optional("PROVISIONING_NOTIFICATION_RECIPIENTS");
        return value == null ? List.of() : Arrays.stream(value.split(",", -1)).map(String::trim).toList();
    }

    private List<WorkspaceProvisioningCommand.InitialService> parseServices() {
        String value = optional("PROVISIONING_INITIAL_SERVICES");
        if (value == null) return List.of();
        List<WorkspaceProvisioningCommand.InitialService> result = new ArrayList<>();
        for (String definition : value.split(";", -1)) {
            String[] parts = definition.split("\\|", -1);
            if (parts.length != 3 || !(parts[0].equals("true") || parts[0].equals("false"))) {
                throw new IllegalStateException("Initial service input is malformed");
            }
            result.add(new WorkspaceProvisioningCommand.InitialService(
                    parts[1], parts[2].isBlank() ? null : parts[2], Boolean.parseBoolean(parts[0])));
        }
        return List.copyOf(result);
    }
}
