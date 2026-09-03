package com.mohammadmurrar.leadflow.notification;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.lead.LeadPriority;
import com.mohammadmurrar.leadflow.notification.api.NotificationResponse;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.UUID;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

@Service
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository repository;
    private final CurrentWorkspace currentWorkspace;

    public NotificationService(NotificationRepository repository, CurrentWorkspace currentWorkspace) {
        this.repository = repository;
        this.currentWorkspace = currentWorkspace;
    }

    public Page<NotificationResponse> findAll(Pageable pageable) {
        return repository.findAllByWorkspaceId(currentWorkspace.requireActiveId(), pageable)
                .map(NotificationResponse::from);
    }

    public long getUnreadCount() {
        return repository.countUnreadByWorkspaceId(currentWorkspace.requireActiveId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createNewLeadNotification(Lead lead) {
        Objects.requireNonNull(lead, "Lead is required");
        String company = lead.getCompany();
        String message = company == null || company.isBlank()
                ? lead.getFullName() + " was added to the pipeline."
                : lead.getFullName() + " from " + company + " was added to the pipeline.";
        repository.save(Notification.create(NotificationType.NEW_LEAD, NotificationSeverity.INFO,
                "New lead received", message, lead));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createQualificationNotification(Lead lead) {
        Objects.requireNonNull(lead, "Lead is required");
        if (lead.getPriority() == LeadPriority.HIGH) {
            repository.save(Notification.create(NotificationType.HIGH_PRIORITY_LEAD,
                    NotificationSeverity.WARNING, "High-priority lead detected",
                    lead.getFullName() + " was qualified with a score of "
                            + lead.getQualificationScore() + " and marked as high priority.", lead));
            return;
        }
        repository.save(Notification.create(NotificationType.LEAD_QUALIFIED,
                NotificationSeverity.SUCCESS, "Lead qualification completed",
                lead.getFullName() + " was qualified with a score of "
                        + lead.getQualificationScore() + ".", lead));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void createAutomationFailedNotification(Lead lead) {
        Objects.requireNonNull(lead, "Lead is required");
        repository.save(Notification.create(NotificationType.AUTOMATION_FAILED,
                NotificationSeverity.ERROR, "Lead qualification failed",
                "Automated qualification did not complete for " + lead.getFullName() + ".", lead));
    }

    @Transactional
    public void markAsRead(UUID id) {
        Notification notification = repository.findByIdAndWorkspaceId(id, currentWorkspace.requireActiveId())
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead() {
        repository.findUnreadByWorkspaceId(currentWorkspace.requireActiveId())
                .forEach(Notification::markAsRead);
    }
}
