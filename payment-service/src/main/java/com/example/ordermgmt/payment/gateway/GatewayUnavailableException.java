package com.example.ordermgmt.payment.gateway;

/**
 * The payment provider could not be reached or answered with a server error.
 *
 * <p>This is a <em>technical</em> failure, unlike a card decline. It is deliberately not caught: it
 * makes the Kafka listener fail, which triggers retries with backoff and finally the dead-letter
 * topic. That is the right behaviour for an outage: do not tell the customer "payment failed" just
 * because our provider had a bad minute.
 */
public class GatewayUnavailableException extends RuntimeException {

    public GatewayUnavailableException(String message) {
        super(message);
    }
}
