package com.example.ordermgmt.contract.events;


import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Payment Service when the gateway declined the charge. The Order Service
 * cancels the order, which in turn makes Inventory release the stock.
 *
 * @param orderId   the order
 * @param paymentId id of the payment record
 * @param reason    why the payment failed
 */
public record PaymentFailedEvent(UUID orderId, UUID paymentId, String reason) implements EventPayload {
}
