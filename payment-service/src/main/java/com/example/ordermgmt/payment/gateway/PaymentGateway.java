package com.example.ordermgmt.payment.gateway;

import java.math.BigDecimal;

/**
 * Port to an external payment provider. The real implementation would call Stripe, Adyen, etc.;
 * this project ships {@link MockPaymentGateway}.
 *
 * <p>Both operations take an <em>idempotency key</em>. Real providers use it to guarantee that
 * repeating a call (for example after a timeout) does not charge twice.
 */
public interface PaymentGateway {

    /**
     * Charges a payment method.
     *
     * @param idempotencyKey stable key for this charge (we use the order id)
     * @param paymentMethod  opaque token identifying the customer's payment method
     * @param amount         amount to charge
     * @param currency       ISO 4217 code
     * @return the outcome; a decline is a normal result, not an exception
     * @throws GatewayUnavailableException if the provider cannot be reached (transient, worth retrying)
     */
    GatewayResult charge(String idempotencyKey, String paymentMethod, BigDecimal amount, String currency);

    /**
     * Refunds a previous charge.
     *
     * @param gatewayReference reference returned by {@link #charge}
     * @param amount           amount to refund
     * @throws GatewayUnavailableException if the provider cannot be reached
     */
    void refund(String gatewayReference, BigDecimal amount);

    /**
     * Outcome of a charge attempt.
     *
     * @param success       whether the money was captured
     * @param reference     the provider's transaction reference
     * @param declineReason why it was declined, when {@code success} is false
     */
    record GatewayResult(boolean success, String reference, String declineReason) {
    }
}
