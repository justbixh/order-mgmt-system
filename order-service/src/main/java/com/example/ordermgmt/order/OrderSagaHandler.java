package com.example.ordermgmt.order;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.events.InventoryReservationExpiredEvent;
import com.example.ordermgmt.contract.events.InventoryReservationFailedEvent;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.messaging.OutboxService;
import com.example.ordermgmt.order.domain.Order;
import com.example.ordermgmt.order.domain.OrderNotFoundException;
import com.example.ordermgmt.order.domain.OrderStatus;
import com.example.ordermgmt.order.domain.OrderRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The Order Service's part of the <strong>saga</strong>: it reacts to events published by the
 * Inventory and Payment services and moves the order forward or cancels it.
 *
 * <h2>Choreography in one picture</h2>
 * <pre>
 *  Order ──OrderCreated──► Inventory ──InventoryReserved──► Payment ──PaymentSucceeded──► Order (CONFIRMED)
 *                              │                               │                            └─OrderConfirmed─► Notification
 *                              └─InventoryReservationFailed─┐  └─PaymentFailed─┐
 *                                                           ▼                  ▼
 *                                                     Order (CANCELLED) ──OrderCancelled──► Inventory releases stock,
 *                                                                                           Payment refunds, Notification tells customer
 * </pre>
 * Nobody tells the services what to do; each one reacts to the events it cares about.
 *
 * <p>Handlers run inside the transaction opened by the idempotent event processor, so the status
 * change and the follow-up event are committed atomically (outbox).
 *
 * <p>Every handler is written to be tolerant: it checks the current status first and ignores events
 * that no longer apply (for example a payment result for an order the customer already cancelled).
 */
@Component
public class OrderSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaHandler.class);

    private final OrderRepository orders;
    private final OutboxService outbox;

    public OrderSagaHandler(OrderRepository orders, OutboxService outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Routes an incoming event to the matching handler; events this service does not care about are ignored.
     *
     * @param event the parsed event
     */
    public void handle(EventEnvelope<? extends EventPayload> event) {
        switch (event.payload()) {
            case InventoryReservedEvent e -> onInventoryReserved(e.orderId());
            case InventoryReservationFailedEvent e -> cancel(e.orderId(), "Out of stock: " + e.reason());
            case InventoryReservationExpiredEvent e -> cancel(e.orderId(), e.reason());
            case PaymentSucceededEvent e -> onPaymentSucceeded(e.orderId());
            case PaymentFailedEvent e -> cancel(e.orderId(), "Payment failed: " + e.reason());
            default -> log.debug("No action for {}", event.eventType());
        }
    }

    private void onInventoryReserved(UUID orderId) {
        Order order = load(orderId);
        if (order.getStatus().isTerminal()) {
            log.info("Order {} is already {}, ignoring InventoryReserved", orderId, order.getStatus());
            return;
        }
        // Another path (a fast PaymentSucceeded) may already have moved the order past this step.
        if (order.getStatus() == OrderStatus.PENDING) {
            order.markInventoryReserved();
            log.info("Order {} -> {}", orderId, order.getStatus());
        }
    }

    private void onPaymentSucceeded(UUID orderId) {
        Order order = load(orderId);
        if (order.getStatus().isTerminal()) {
            // Example: the customer cancelled while the payment was in flight. The Payment Service
            // sees OrderCancelled and refunds, so nothing to do here.
            log.warn("Order {} is already {}, ignoring PaymentSucceeded (Payment Service will refund if cancelled)",
                    orderId, order.getStatus());
            return;
        }
        order.confirm();
        outbox.publish(new OrderConfirmedEvent(order.getId(), order.getCustomerId(), order.getCustomerEmail(),
                order.getTotalAmount(), order.getCurrency()));
        log.info("Order {} -> CONFIRMED", orderId);
    }

    /**
     * Cancels the order and publishes {@code OrderCancelled}, the trigger for all compensations.
     */
    private void cancel(UUID orderId, String reason) {
        Order order = load(orderId);
        if (order.getStatus().isTerminal()) {
            log.info("Order {} is already {}, not cancelling again ({})", orderId, order.getStatus(), reason);
            return;
        }
        order.cancel(reason);
        outbox.publish(new OrderCancelledEvent(order.getId(), order.getCustomerId(), order.getCustomerEmail(), reason));
        log.info("Order {} -> CANCELLED: {}", orderId, reason);
    }

    /** An unknown order here means corrupt data, so fail loudly: retries, then the dead-letter topic. */
    private Order load(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
