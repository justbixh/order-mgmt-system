package com.example.ordermgmt.notification.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import com.example.ordermgmt.messaging.EventDispatcher;
import com.example.ordermgmt.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/** Kafka consumer of the Notification Service: listens to the final outcomes on the order topic. */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final EventDispatcher dispatcher;
    private final NotificationService notifications;

    public NotificationEventListener(EventDispatcher dispatcher, NotificationService notifications) {
        this.dispatcher = dispatcher;
        this.notifications = notifications;
    }

    /**
     * @param message the raw JSON event
     * @param ack     handle used to commit the offset
     */
    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "notification-service")
    public void onEvent(String message, Acknowledgment ack) {
        dispatcher.dispatch(message, this::handle);
        ack.acknowledge();
    }

    private void handle(EventEnvelope<? extends EventPayload> event) {
        switch (event.payload()) {
            case OrderConfirmedEvent e -> notifications.onOrderConfirmed(e);
            case OrderCancelledEvent e -> notifications.onOrderCancelled(e);
            default -> log.debug("No action for {}", event.eventType());
        }
    }
}
