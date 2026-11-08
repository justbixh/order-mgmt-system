package com.example.ordermgmt.payment.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.messaging.EventDispatcher;
import com.example.ordermgmt.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer of the Payment Service. Listens to inventory events (to charge) and order events
 * (to refund on cancellation).
 */
@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final EventDispatcher dispatcher;
    private final PaymentService payments;

    public PaymentEventListener(EventDispatcher dispatcher, PaymentService payments) {
        this.dispatcher = dispatcher;
        this.payments = payments;
    }

    /**
     * @param message the raw JSON event
     * @param ack     handle used to commit the offset
     */
    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.ORDER_EVENTS}, groupId = "payment-service")
    public void onEvent(String message, Acknowledgment ack) {
        dispatcher.dispatch(message, this::handle);
        ack.acknowledge();
    }

    private void handle(EventEnvelope<? extends EventPayload> event) {
        switch (event.payload()) {
            case InventoryReservedEvent e -> payments.charge(e);
            case OrderCancelledEvent e -> payments.onOrderCancelled(e.orderId());
            default -> log.debug("No action for {}", event.eventType());
        }
    }
}
