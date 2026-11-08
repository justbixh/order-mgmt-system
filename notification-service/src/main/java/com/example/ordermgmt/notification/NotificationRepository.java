package com.example.ordermgmt.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Notification}. */
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByOrderIdOrderByCreatedAt(UUID orderId);

    List<Notification> findAllByOrderByCreatedAtDesc();
}
