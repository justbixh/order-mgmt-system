package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;
import java.util.List;
import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Order Service when a customer places an order. This starts the saga.
 *
 * <p>Carries everything downstream services need, so they never have to call the Order Service
 * back (which would couple them at runtime).
 *
 * @param orderId       id of the new order
 * @param customerId    who placed it
 * @param customerEmail where notifications are sent
 * @param items         the order lines to reserve stock for
 * @param totalAmount   sum of all lines
 * @param currency      ISO 4217 currency code
 * @param paymentMethod opaque payment token chosen by the customer (see the mock gateway)
 */
public record OrderCreatedEvent(UUID orderId, String customerId, String customerEmail, List<OrderLine> items, BigDecimal totalAmount, String currency, String paymentMethod) implements EventPayload {
}
