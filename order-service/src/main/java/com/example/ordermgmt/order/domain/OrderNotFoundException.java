package com.example.ordermgmt.order.domain;

import java.util.UUID;

/** Thrown when an order id does not exist. Mapped to HTTP 404 by the REST layer. */
public class OrderNotFoundException extends RuntimeException {

    /** @param orderId the id that was looked up */
    public OrderNotFoundException(UUID orderId) {
        super("Order " + orderId + " not found");
    }
}
