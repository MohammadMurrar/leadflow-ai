package com.mohammadmurrar.leadflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    long countByReadAtIsNull();
    List<Notification> findAllByReadAtIsNull();
}
