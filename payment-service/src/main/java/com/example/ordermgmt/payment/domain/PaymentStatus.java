package com.example.ordermgmt.payment.domain;

/** Lifecycle of a payment. */
public enum PaymentStatus {
    /** Charged successfully. */
    COMPLETED,
    /** The gateway declined the charge. */
    FAILED,
    /** A completed charge was returned because the order was cancelled. */
    REFUNDED,
    /**
     * The order was cancelled <em>before</em> we charged. This placeholder row makes a late
     * {@code InventoryReserved} for the same order a no-op, so a cancelled order is never charged.
     */
    CANCELLED
}
