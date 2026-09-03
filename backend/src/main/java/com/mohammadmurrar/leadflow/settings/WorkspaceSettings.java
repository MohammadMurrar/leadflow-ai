package com.mohammadmurrar.leadflow.settings;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import com.mohammadmurrar.leadflow.workspace.Workspace;

@Entity
@Table(name = "workspace_settings", uniqueConstraints =
        @UniqueConstraint(name = "uk_workspace_settings_workspace", columnNames = "workspace_id"))
public class WorkspaceSettings {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(name = "singleton_key", nullable = false)
    private byte singletonKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "workspace_name", nullable = false, length = 120)
    private String workspaceName;

    @Column(name = "contact_email", length = 180)
    private String contactEmail;

    @Column(length = 500)
    private String description;

    @Column(name = "public_brand_name", length = 120)
    private String publicBrandName;

    @Column(name = "public_tagline", length = 240)
    private String publicTagline;

    @Column(name = "public_logo_path", length = 500)
    private String publicLogoPath;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "response_time_text", nullable = false, length = 240)
    private String responseTimeText;

    @Column(name = "privacy_policy_url", length = 2048)
    private String privacyPolicyUrl;

    @Column(name = "privacy_notice_text", length = 1000)
    private String privacyNoticeText;

    @Column(name = "privacy_notice_version", length = 64)
    private String privacyNoticeVersion;

    @OneToMany(mappedBy = "workspaceSettings", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("normalizedEmail ASC")
    private List<WorkspaceNotificationRecipient> notificationRecipients = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceSettings() {}

    public static WorkspaceSettings createNeutral() {
        WorkspaceSettings settings = new WorkspaceSettings();
        settings.id = UUID.randomUUID();
        settings.singletonKey = 1;
        settings.workspaceName = "My Workspace";
        settings.timeZone = "UTC";
        settings.currency = "USD";
        settings.responseTimeText = "We usually respond within one business day.";
        return settings;
    }

    public static WorkspaceSettings createNeutral(Workspace workspace, byte legacyKey) {
        WorkspaceSettings settings = createNeutral();
        settings.workspace = Objects.requireNonNull(workspace, "Workspace is required");
        settings.singletonKey = legacyKey;
        return settings;
    }

    public boolean update(String workspaceName, String contactEmail, String description,
            String publicBrandName, String publicTagline, String publicLogoPath,
            String timeZone, String currency, String responseTimeText,
            String privacyPolicyUrl, String privacyNoticeText, String privacyNoticeVersion,
            List<String> notificationRecipients) {
        String normalizedName = normalizeName(workspaceName);
        String normalizedEmail = normalizeOptional(contactEmail);
        String normalizedDescription = normalizeOptional(description);
        List<String> currentRecipients = getNotificationRecipients();
        if (Objects.equals(this.workspaceName, normalizedName)
                && Objects.equals(this.contactEmail, normalizedEmail)
                && Objects.equals(this.description, normalizedDescription)
                && Objects.equals(this.publicBrandName, publicBrandName)
                && Objects.equals(this.publicTagline, publicTagline)
                && Objects.equals(this.publicLogoPath, publicLogoPath)
                && Objects.equals(this.timeZone, timeZone)
                && Objects.equals(this.currency, currency)
                && Objects.equals(this.responseTimeText, responseTimeText)
                && Objects.equals(this.privacyPolicyUrl, privacyPolicyUrl)
                && Objects.equals(this.privacyNoticeText, privacyNoticeText)
                && Objects.equals(this.privacyNoticeVersion, privacyNoticeVersion)
                && currentRecipients.equals(notificationRecipients)) {
            return false;
        }
        this.workspaceName = normalizedName;
        this.contactEmail = normalizedEmail;
        this.description = normalizedDescription;
        this.publicBrandName = publicBrandName;
        this.publicTagline = publicTagline;
        this.publicLogoPath = publicLogoPath;
        this.timeZone = timeZone;
        this.currency = currency;
        this.responseTimeText = responseTimeText;
        this.privacyPolicyUrl = privacyPolicyUrl;
        this.privacyNoticeText = privacyNoticeText;
        this.privacyNoticeVersion = privacyNoticeVersion;
        if (!currentRecipients.equals(notificationRecipients)) {
            Set<String> desiredRecipients = new HashSet<>(notificationRecipients);
            this.notificationRecipients.removeIf(recipient ->
                    !desiredRecipients.contains(recipient.getNormalizedEmail()));
            Set<String> retainedRecipients = this.notificationRecipients.stream()
                    .map(WorkspaceNotificationRecipient::getNormalizedEmail)
                    .collect(java.util.stream.Collectors.toSet());
            notificationRecipients.stream()
                    .filter(email -> !retainedRecipients.contains(email))
                    .forEach(email -> this.notificationRecipients.add(
                            WorkspaceNotificationRecipient.create(this, email)));
            // The child owns the foreign key, so explicitly dirty the versioned parent.
            this.updatedAt = Instant.now();
        }
        return true;
    }

    private static String normalizeName(String value) {
        if (value == null || value.isBlank()) throw new InvalidWorkspaceNameException();
        String normalized = value.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 120) throw new InvalidWorkspaceNameException();
        return normalized;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public long getVersion() { return version; }
    public String getWorkspaceName() { return workspaceName; }
    public String getContactEmail() { return contactEmail; }
    public String getDescription() { return description; }
    public String getPublicBrandName() { return publicBrandName; }
    public String getEffectivePublicBrandName() {
        return publicBrandName == null ? workspaceName : publicBrandName;
    }
    public String getPublicTagline() { return publicTagline; }
    public String getPublicLogoPath() { return publicLogoPath; }
    public String getTimeZone() { return timeZone; }
    public String getCurrency() { return currency; }
    public String getResponseTimeText() { return responseTimeText; }
    public String getPrivacyPolicyUrl() { return privacyPolicyUrl; }
    public String getPrivacyNoticeText() { return privacyNoticeText; }
    public String getPrivacyNoticeVersion() { return privacyNoticeVersion; }
    public List<String> getNotificationRecipients() {
        return notificationRecipients.stream()
                .map(WorkspaceNotificationRecipient::getNormalizedEmail).sorted().toList();
    }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class InvalidWorkspaceNameException extends RuntimeException {}
}
