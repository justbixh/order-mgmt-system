package com.example.ordermgmt.order.domain;

import java.util.UUID;

/** Thrown when a customer tries to cancel an order that is already final. Mapped to HTTP 409. */
public class OrderNotCancellableException extends RuntimeException {

    /**
     * @param orderId the order
     * @param status  its current status
     */
    public OrderNotCancellableException(UUID orderId, OrderStatus status) {
        super("Order " + orderId + " is " + status + " and can no longer be cancelled");
    }
}
