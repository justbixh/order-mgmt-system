package com.example.ordermgmt.contract.events;


import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Order Service when an order is cancelled, either because a saga step failed
 * or because the customer asked for it. This is the <strong>compensation trigger</strong>:
 * Inventory releases reserved stock and Payment refunds a charge when they see it.
 *
 * @param orderId       the cancelled order
 * @param customerId    who placed it
 * @param customerEmail where to send the cancellation notice
 * @param reason        human readable reason, e.g. "Payment declined"
 */
public record OrderCancelledEvent(UUID orderId, String customerId, String customerEmail, String reason) implements EventPayload {
}
