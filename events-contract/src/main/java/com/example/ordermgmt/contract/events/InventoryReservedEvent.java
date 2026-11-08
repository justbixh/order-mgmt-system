package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;
import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Inventory Service when stock was reserved for every line of an order.
 *
 * <p>It forwards the amount and payment method so the Payment Service can charge without calling
 * anyone.
 *
 * @param orderId       the order
 * @param customerId    who to charge
 * @param totalAmount   amount to charge
 * @param currency      ISO 4217 currency code
 * @param paymentMethod opaque payment token
 */
public record InventoryReservedEvent(UUID orderId, String customerId, BigDecimal totalAmount, String currency, String paymentMethod) implements EventPayload {
}
