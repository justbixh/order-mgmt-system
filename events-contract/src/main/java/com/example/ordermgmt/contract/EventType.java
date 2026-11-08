package com.example.ordermgmt.contract;

import com.example.ordermgmt.contract.events.InventoryReleasedEvent;
import com.example.ordermgmt.contract.events.InventoryReservationExpiredEvent;
import com.example.ordermgmt.contract.events.InventoryReservationFailedEvent;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import com.example.ordermgmt.contract.events.PaymentRefundedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import java.util.Arrays;
import java.util.Optional;

/**
 * Registry of every event in the system: its wire name, the topic it is published to, and the
 * Java class of its payload.
 *
 * <p>This is the single place that answers "which topic does this event go to?" and "which class
 * do I deserialize this JSON into?". Adding a new event means adding one line here plus a payload
 * record.
 */
public enum EventType {

    // ---- published by the Order Service -------------------------------------------------
    ORDER_CREATED("OrderCreated", Topics.ORDER_EVENTS, OrderCreatedEvent.class),
    ORDER_CONFIRMED("OrderConfirmed", Topics.ORDER_EVENTS, OrderConfirmedEvent.class),
    ORDER_CANCELLED("OrderCancelled", Topics.ORDER_EVENTS, OrderCancelledEvent.class),

    // ---- published by the Inventory Service ---------------------------------------------
    INVENTORY_RESERVED("InventoryReserved", Topics.INVENTORY_EVENTS, InventoryReservedEvent.class),
    INVENTORY_RESERVATION_FAILED("InventoryReservationFailed", Topics.INVENTORY_EVENTS,
            InventoryReservationFailedEvent.class),
    INVENTORY_RESERVATION_EXPIRED("InventoryReservationExpired", Topics.INVENTORY_EVENTS,
            InventoryReservationExpiredEvent.class),
    INVENTORY_RELEASED("InventoryReleased", Topics.INVENTORY_EVENTS, InventoryReleasedEvent.class),

    // ---- published by the Payment Service -----------------------------------------------
    PAYMENT_SUCCEEDED("PaymentSucceeded", Topics.PAYMENT_EVENTS, PaymentSucceededEvent.class),
    PAYMENT_FAILED("PaymentFailed", Topics.PAYMENT_EVENTS, PaymentFailedEvent.class),
    PAYMENT_REFUNDED("PaymentRefunded", Topics.PAYMENT_EVENTS, PaymentRefundedEvent.class);

    private final String eventName;
    private final String topic;
    private final Class<? extends EventPayload> payloadClass;

    EventType(String eventName, String topic, Class<? extends EventPayload> payloadClass) {
        this.eventName = eventName;
        this.topic = topic;
        this.payloadClass = payloadClass;
    }

    /** The name written into {@link EventEnvelope#eventType()}. */
    public String eventName() {
        return eventName;
    }

    /** The topic this event is published to. */
    public String topic() {
        return topic;
    }

    /** The class the payload JSON is deserialized into. */
    public Class<? extends EventPayload> payloadClass() {
        return payloadClass;
    }

    /**
     * Looks an event up by its wire name.
     *
     * @return the type, or empty if the name is unknown (e.g. a newer producer added an event
     *         this build does not know about yet)
     */
    public static Optional<EventType> byName(String eventName) {
        return Arrays.stream(values()).filter(t -> t.eventName.equals(eventName)).findFirst();
    }

    /**
     * Looks an event up by its payload class.
     *
     * @throws IllegalArgumentException if the class is not registered
     */
    public static EventType forPayload(Class<? extends EventPayload> payloadClass) {
        return Arrays.stream(values())
                .filter(t -> t.payloadClass.equals(payloadClass))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unregistered event payload: " + payloadClass));
    }
}
