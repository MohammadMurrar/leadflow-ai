package com.mohammadmurrar.leadflow.notification.api;

import com.mohammadmurrar.leadflow.notification.NotificationService;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService service;

    public NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    public Page<NotificationResponse> findAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/unread-count")
    public UnreadNotificationCountResponse getUnreadCount() {
        return new UnreadNotificationCountResponse(service.getUnreadCount());
    }

    @PatchMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead() {
        service.markAllAsRead();
    }

    @PatchMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable UUID id) {
        service.markAsRead(id);
    }
}

