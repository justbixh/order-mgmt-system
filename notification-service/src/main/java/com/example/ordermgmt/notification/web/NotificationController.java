package com.example.ordermgmt.notification.web;

import com.example.ordermgmt.notification.Notification;
import com.example.ordermgmt.notification.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only API to see which notifications were sent. */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    /** Notification as returned by the API. */
    public record NotificationView(UUID id, UUID orderId, String recipient, String type, String subject,
                                   String body, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getOrderId(), n.getRecipient(), n.getType(), n.getSubject(),
                    n.getBody(), n.getCreatedAt());
        }
    }

    @GetMapping
    @Operation(summary = "List notifications, optionally for one order")
    public List<NotificationView> list(@RequestParam(required = false) UUID orderId) {
        var found = orderId == null
                ? notifications.findAllByOrderByCreatedAtDesc()
                : notifications.findByOrderIdOrderByCreatedAt(orderId);
        return found.stream().map(NotificationView::from).toList();
    }
}
