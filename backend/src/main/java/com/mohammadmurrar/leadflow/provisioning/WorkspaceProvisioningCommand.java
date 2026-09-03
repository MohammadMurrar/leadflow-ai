package com.mohammadmurrar.leadflow.provisioning;

import java.util.List;

public final class WorkspaceProvisioningCommand {
    private final String workspaceDisplayName;
    private final String publicSlug;
    private final String publicDescription;
    private final String administratorDisplayName;
    private final String administratorEmail;
    private final char[] administratorPassword;
    private final List<InitialService> initialServices;
    private final List<String> notificationRecipients;

    public WorkspaceProvisioningCommand(String workspaceDisplayName, String publicSlug,
            String publicDescription, String administratorDisplayName, String administratorEmail,
            char[] administratorPassword, List<InitialService> initialServices,
            List<String> notificationRecipients) {
        this.workspaceDisplayName = workspaceDisplayName;
        this.publicSlug = publicSlug;
        this.publicDescription = publicDescription;
        this.administratorDisplayName = administratorDisplayName;
        this.administratorEmail = administratorEmail;
        this.administratorPassword = administratorPassword == null ? null : administratorPassword.clone();
        this.initialServices = initialServices == null ? List.of() : List.copyOf(initialServices);
        this.notificationRecipients = notificationRecipients == null
                ? List.of() : List.copyOf(notificationRecipients);
    }

    public String workspaceDisplayName() { return workspaceDisplayName; }
    public String publicSlug() { return publicSlug; }
    public String publicDescription() { return publicDescription; }
    public String administratorDisplayName() { return administratorDisplayName; }
    public String administratorEmail() { return administratorEmail; }
    public char[] administratorPassword() {
        return administratorPassword == null ? null : administratorPassword.clone();
    }
    void clearPassword() {
        if (administratorPassword != null) java.util.Arrays.fill(administratorPassword, '\0');
    }
    public List<InitialService> initialServices() { return initialServices; }
    public List<String> notificationRecipients() { return notificationRecipients; }

    @Override
    public String toString() { return "WorkspaceProvisioningCommand[redacted]"; }

    public record InitialService(String name, String description, boolean active) {
        @Override public String toString() { return "InitialService[redacted]"; }
    }
}
