package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;
import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Payment Service when the customer was charged successfully.
 *
 * @param orderId   the order
 * @param paymentId id of the payment record
 * @param amount    amount charged
 * @param currency  ISO 4217 currency code
 */
public record PaymentSucceededEvent(UUID orderId, UUID paymentId, BigDecimal amount, String currency) implements EventPayload {
}
