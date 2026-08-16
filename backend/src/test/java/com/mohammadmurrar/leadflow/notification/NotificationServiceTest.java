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

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationRepository repository;
    @InjectMocks NotificationService service;

    @Test
    void returnsMappedNotificationsAndPreservesPagination() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Lead lead = mock(Lead.class);
        UUID leadId = UUID.randomUUID();
        when(lead.getId()).thenReturn(leadId);
        Notification withLead = notification("With lead", lead);
        Notification withoutLead = notification("Without lead", null);
        when(repository.findAll(pageable)).thenReturn(new PageImpl<>(
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
        when(repository.countByReadAtIsNull()).thenReturn(3L);

        assertThat(service.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    void marksUnreadNotificationWithoutSavingExplicitly() {
        UUID id = UUID.randomUUID();
        Notification notification = notification("Unread", null);
        when(repository.findById(id)).thenReturn(Optional.of(notification));

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
        when(repository.findById(id)).thenReturn(Optional.of(notification));

        service.markAsRead(id);

        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
        verify(repository, never()).save(any());
    }

    @Test
    void throwsNotFoundForUnknownNotification() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Notification not found: " + id);
        verify(repository, never()).save(any());
    }

    @Test
    void marksEveryUnreadNotification() {
        Notification first = notification("First", null);
        Notification second = notification("Second", null);
        when(repository.findAllByReadAtIsNull()).thenReturn(List.of(first, second));

        service.markAllAsRead();

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
        verify(repository).findAllByReadAtIsNull();
    }

    @Test
    void handlesEmptyUnreadNotificationList() {
        when(repository.findAllByReadAtIsNull()).thenReturn(List.of());

        service.markAllAsRead();

        verify(repository).findAllByReadAtIsNull();
        verifyNoMoreInteractions(repository);
    }

    private Notification notification(String title, Lead lead) {
        return Notification.create(NotificationType.NEW_LEAD, NotificationSeverity.INFO,
                title, "Notification service test message.", lead);
    }
}
