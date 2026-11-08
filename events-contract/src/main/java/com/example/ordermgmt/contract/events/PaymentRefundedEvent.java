package com.example.ordermgmt.contract.events;

import java.math.BigDecimal;
import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Payment Service after it refunded a charge because the order was cancelled.
 *
 * @param orderId   the order
 * @param paymentId id of the payment record
 * @param amount    amount refunded
 * @param currency  ISO 4217 currency code
 */
public record PaymentRefundedEvent(UUID orderId, UUID paymentId, BigDecimal amount, String currency) implements EventPayload {
}
