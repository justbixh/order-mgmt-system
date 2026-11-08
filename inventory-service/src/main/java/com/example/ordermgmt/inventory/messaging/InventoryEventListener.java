package com.example.ordermgmt.inventory.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.inventory.ReservationService;
import com.example.ordermgmt.messaging.EventDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer of the Inventory Service. Listens to order events (to reserve and release) and
 * payment events (to commit). See {@code OrderEventListener} in the Order Service for how manual
 * acknowledgement and retries work; the mechanics are identical.
 */
@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final EventDispatcher dispatcher;
    private final ReservationService reservations;

    public InventoryEventListener(EventDispatcher dispatcher, ReservationService reservations) {
        this.dispatcher = dispatcher;
        this.reservations = reservations;
    }

    /**
     * @param message the raw JSON event
     * @param ack     handle used to commit the offset
     */
    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS}, groupId = "inventory-service")
    public void onEvent(String message, Acknowledgment ack) {
        dispatcher.dispatch(message, this::handle);
        ack.acknowledge();
    }

    private void handle(EventEnvelope<? extends EventPayload> event) {
        switch (event.payload()) {
            case OrderCreatedEvent e -> reservations.reserve(e);
            case PaymentSucceededEvent e -> reservations.commit(e.orderId());
            case OrderCancelledEvent e -> reservations.release(e.orderId(), e.reason());
            default -> log.debug("No action for {}", event.eventType());
        }
    }
}
