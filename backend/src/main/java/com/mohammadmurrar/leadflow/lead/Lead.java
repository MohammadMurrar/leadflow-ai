package com.mohammadmurrar.leadflow.lead;

import com.mohammadmurrar.leadflow.service.ServiceOffering;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "leads", indexes = {
        @Index(name = "idx_leads_status_created", columnList = "status,created_at"),
        @Index(name = "idx_leads_email", columnList = "email")
})
public class Lead {
    @Id
    private UUID id;

    @Version
    private long version;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 140)
    private String company;

    @Column(nullable = false, length = 120)
    private String requestedService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ServiceOffering service;

    @Column(precision = 12, scale = 2)
    private BigDecimal estimatedBudget;

    private LocalDate desiredStartDate;

    @Column(nullable = false, length = 3000)
    private String message;

    @Column(nullable = false, length = 60)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadPriority priority;

    private Integer qualificationScore;

    @Column(length = 120)
    private String category;

    @Column(length = 1200)
    private String aiSummary;

    @Column(length = 1800)
    private String recommendedReply;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Lead() {}

    public static Lead create(String fullName, String email, String phone, String company,
                              String requestedService, BigDecimal estimatedBudget,
                              LocalDate desiredStartDate, String message, String source) {
        return create(fullName, email, phone, company, requestedService, null,
                estimatedBudget, desiredStartDate, message, source);
    }

    public static Lead create(String fullName, String email, String phone, String company,
                              String requestedService, ServiceOffering service, BigDecimal estimatedBudget,
                              LocalDate desiredStartDate, String message, String source) {
        Lead lead = new Lead();
        lead.id = UUID.randomUUID();
        lead.fullName = fullName.trim();
        lead.email = email.trim().toLowerCase();
        lead.phone = normalize(phone);
        lead.company = normalize(company);
        lead.requestedService = requestedService.trim();
        lead.service = service;
        lead.estimatedBudget = estimatedBudget;
        lead.desiredStartDate = desiredStartDate;
        lead.message = message.trim();
        lead.source = source.trim();
        lead.status = LeadStatus.NEW;
        lead.priority = LeadPriority.UNASSESSED;
        return lead;
    }

    public void startQualification() {
        status = LeadStatus.QUALIFYING;
    }

    public void retryQualification() {
        if (status != LeadStatus.AUTOMATION_FAILED) {
            throw new InvalidStatusTransitionException(status, LeadStatus.QUALIFYING);
        }
        status = LeadStatus.QUALIFYING;
    }

    public void applyQualification(int score, LeadPriority priority, String category,
                                   String summary, String reply) {
        if (status != LeadStatus.QUALIFYING) {
            throw new InvalidStatusTransitionException(status, LeadStatus.QUALIFIED);
        }
        this.qualificationScore = score;
        this.priority = priority;
        this.category = category.trim();
        this.aiSummary = summary.trim();
        this.recommendedReply = reply.trim();
        this.status = LeadStatus.QUALIFIED;
    }

    public void markAutomationFailed() {
        if (status != LeadStatus.QUALIFYING) {
            throw new InvalidStatusTransitionException(status, LeadStatus.AUTOMATION_FAILED);
        }
        status = LeadStatus.AUTOMATION_FAILED;
    }

    public boolean transitionTo(LeadStatus target) {
        if (status == target) {
            return false;
        }
        boolean allowed = switch (status) {
            case QUALIFIED -> target == LeadStatus.CONTACTED
                    || target == LeadStatus.WON
                    || target == LeadStatus.LOST;
            case CONTACTED -> target == LeadStatus.WON || target == LeadStatus.LOST;
            default -> false;
        };
        if (!allowed) {
            throw new InvalidStatusTransitionException(status, target);
        }
        status = target;
        return true;
    }

    public boolean hasSuccessfullyQualified() {
        return status == LeadStatus.QUALIFIED
                || status == LeadStatus.CONTACTED
                || status == LeadStatus.WON
                || status == LeadStatus.LOST;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public long getVersion() { return version; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getCompany() { return company; }
    public String getRequestedService() { return requestedService; }
    public ServiceOffering getService() { return service; }
    public BigDecimal getEstimatedBudget() { return estimatedBudget; }
    public LocalDate getDesiredStartDate() { return desiredStartDate; }
    public String getMessage() { return message; }
    public String getSource() { return source; }
    public LeadStatus getStatus() { return status; }
    public LeadPriority getPriority() { return priority; }
    public Integer getQualificationScore() { return qualificationScore; }
    public String getCategory() { return category; }
    public String getAiSummary() { return aiSummary; }
    public String getRecommendedReply() { return recommendedReply; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static class InvalidStatusTransitionException extends RuntimeException {
        InvalidStatusTransitionException(LeadStatus current, LeadStatus target) {
            super("Lead cannot transition from " + current + " to " + target);
        }
    }
}
