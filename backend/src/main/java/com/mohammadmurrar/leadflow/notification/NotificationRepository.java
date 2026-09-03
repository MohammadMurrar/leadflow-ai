package com.mohammadmurrar.leadflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Query("""
            select notification from Notification notification
            where notification.workspace.id = :workspaceId
              and (notification.lead is null or notification.lead.workspace.id = :workspaceId)
            """)
    Page<Notification> findAllByWorkspaceId(@Param("workspaceId") UUID workspaceId, Pageable pageable);

    @Query("""
            select count(notification) from Notification notification
            where notification.workspace.id = :workspaceId and notification.readAt is null
              and (notification.lead is null or notification.lead.workspace.id = :workspaceId)
            """)
    long countUnreadByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    @Query("""
            select notification from Notification notification
            where notification.id = :id and notification.workspace.id = :workspaceId
              and (notification.lead is null or notification.lead.workspace.id = :workspaceId)
            """)
    Optional<Notification> findByIdAndWorkspaceId(
            @Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    @Query("""
            select notification from Notification notification
            where notification.workspace.id = :workspaceId and notification.readAt is null
              and (notification.lead is null or notification.lead.workspace.id = :workspaceId)
            """)
    List<Notification> findUnreadByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
