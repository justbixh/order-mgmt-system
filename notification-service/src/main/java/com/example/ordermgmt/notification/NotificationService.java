package com.example.ordermgmt.notification;

import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds, "sends" and records notifications for final order outcomes. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationSender sender;

    public NotificationService(NotificationRepository notifications, NotificationSender sender) {
        this.notifications = notifications;
        this.sender = sender;
    }

    /** Sends the order confirmation. */
    @Transactional
    public void onOrderConfirmed(OrderConfirmedEvent e) {
        String subject = "Your order is confirmed";
        String body = "Thank you! Order %s for %s %s has been paid and is confirmed.".formatted(
                e.orderId(), e.totalAmount(), e.currency());
        deliver(e.orderId(), e.customerId(), e.customerEmail(), "ORDER_CONFIRMED", subject, body);
    }

    /** Sends the cancellation notice. */
    @Transactional
    public void onOrderCancelled(OrderCancelledEvent e) {
        String subject = "Your order was cancelled";
        String body = "Order %s was cancelled: %s. Any payment is being refunded.".formatted(e.orderId(), e.reason());
        deliver(e.orderId(), e.customerId(), e.customerEmail(), "ORDER_CANCELLED", subject, body);
    }

    private void deliver(java.util.UUID orderId, String customerId, String email, String type, String subject, String body) {
        sender.send(email, subject, body);
        notifications.save(new Notification(orderId, customerId, email, type, subject, body));
        log.info("Notification {} recorded for order {}", type, orderId);
    }
}
