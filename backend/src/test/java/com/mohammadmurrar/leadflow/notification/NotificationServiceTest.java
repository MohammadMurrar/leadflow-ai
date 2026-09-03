package com.mohammadmurrar.leadflow.notification;

import com.mohammadmurrar.leadflow.common.NotFoundException;
import com.mohammadmurrar.leadflow.lead.Lead;
import com.mohammadmurrar.leadflow.notification.api.NotificationResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mohammadmurrar.leadflow.workspace.CurrentWorkspace;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
    @Mock CurrentWorkspace currentWorkspace;
    @InjectMocks NotificationService service;

    private static final UUID WORKSPACE_ID =
            com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA().getId();

    @Test
    void returnsMappedNotificationsAndPreservesPagination() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Lead lead = mock(Lead.class);
        UUID leadId = UUID.randomUUID();
        when(lead.getId()).thenReturn(leadId);
        Notification withLead = notification("With lead", lead);
        Notification withoutLead = notification("Without lead", null);
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findAllByWorkspaceId(WORKSPACE_ID, pageable)).thenReturn(new PageImpl<>(
                List.of(withLead, withoutLead), pageable, 12));

        Page<NotificationResponse> result = service.findAll(pageable);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(12);
        assertThat(result.getContent()).extracting(NotificationResponse::title)
                .containsExactly("With lead", "Without lead");
        assertThat(result.getContent()).extracting(NotificationResponse::leadId)
                .containsExactly(leadId, null);
    }

    @Test
    void returnsUnreadCount() {
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.countUnreadByWorkspaceId(WORKSPACE_ID)).thenReturn(3L);

        assertThat(service.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    void marksUnreadNotificationWithoutSavingExplicitly() {
        UUID id = UUID.randomUUID();
        Notification notification = notification("Unread", null);
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findByIdAndWorkspaceId(id, WORKSPACE_ID)).thenReturn(Optional.of(notification));

        service.markAsRead(id);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
        verify(repository, never()).save(any());
    }

    @Test
    void markingAlreadyReadNotificationIsIdempotent() {
        UUID id = UUID.randomUUID();
        Notification notification = notification("Already read", null);
        notification.markAsRead();
        Instant firstReadAt = notification.getReadAt();
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findByIdAndWorkspaceId(id, WORKSPACE_ID)).thenReturn(Optional.of(notification));

        service.markAsRead(id);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
        verify(repository, never()).save(any());
    }

    @Test
    void throwsNotFoundForUnknownNotification() {
        UUID id = UUID.randomUUID();
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findByIdAndWorkspaceId(id, WORKSPACE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification not found");
        verify(repository, never()).save(any());
    }

    @Test
    void marksEveryUnreadNotification() {
        Notification first = notification("First", null);
        Notification second = notification("Second", null);
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findUnreadByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of(first, second));

        service.markAllAsRead();

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(repository).findUnreadByWorkspaceId(WORKSPACE_ID);
    }

    @Test
    void handlesEmptyUnreadNotificationList() {
        when(currentWorkspace.requireActiveId()).thenReturn(WORKSPACE_ID);
        when(repository.findUnreadByWorkspaceId(WORKSPACE_ID)).thenReturn(List.of());

        service.markAllAsRead();

        verify(repository).findUnreadByWorkspaceId(WORKSPACE_ID);
        verifyNoMoreInteractions(repository);
    }

    private Notification notification(String title, Lead lead) {
        Lead ownedLead = lead == null ? ownedLead() : lead;
        when(ownedLead.getWorkspace()).thenReturn(
                com.mohammadmurrar.leadflow.support.WorkspaceTestFixtures.activeWorkspaceA());
        Notification notification = Notification.create(NotificationType.NEW_LEAD,
                NotificationSeverity.INFO, title, "Notification service test message.", ownedLead);
        if (lead == null && title.equals("Without lead")) {
            try {
                var field = Notification.class.getDeclaredField("lead");
                field.setAccessible(true);
                field.set(notification, null);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
        return notification;
    }

    private Lead ownedLead() {
        return mock(Lead.class);
    }
}
