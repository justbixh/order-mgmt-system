package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;
import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Order Service once payment succeeded. This is the happy end of the saga.
 *
 * @param orderId       the confirmed order
 * @param customerId    who placed it
 * @param customerEmail where to send the confirmation
 * @param totalAmount   amount charged
 * @param currency      ISO 4217 currency code
 */
public record OrderConfirmedEvent(UUID orderId, String customerId, String customerEmail, BigDecimal totalAmount, String currency) implements EventPayload {
}
