package com.example.ordermgmt.order.domain;

import java.util.Set;

/**
 * Lifecycle of an order. The allowed transitions are the order's part of the saga:
 *
 * <pre>
 *   PENDING ──► INVENTORY_RESERVED ──► CONFIRMED
 *      │               │
 *      └───────────────┴──────────────► CANCELLED
 *      └──────────────────────────────► CONFIRMED   (see note)
 * </pre>
 *
 * <p>Note: {@code PENDING → CONFIRMED} exists because events from different topics have no
 * ordering guarantee between them. A {@code PaymentSucceeded} could in theory be consumed before
 * the {@code InventoryReserved} that caused it, and the order must still end up confirmed.
 */
public enum OrderStatus {

    /** Created, waiting for stock reservation. */
    PENDING,
    /** Stock is reserved, waiting for payment. */
    INVENTORY_RESERVED,
    /** Paid. Final state. */
    CONFIRMED,
    /** Failed or cancelled; compensations are triggered. Final state. */
    CANCELLED;

    /** Whether the order can still change; false for {@link #CONFIRMED} and {@link #CANCELLED}. */
    public boolean isTerminal() {
        return this == CONFIRMED || this == CANCELLED;
    }

    /**
     * @param target the status to move to
     * @return whether the lifecycle allows moving from this status to {@code target}
     */
    public boolean canTransitionTo(OrderStatus target) {
        return allowedTargets().contains(target);
    }

    private Set<OrderStatus> allowedTargets() {
        return switch (this) {
            case PENDING -> Set.of(INVENTORY_RESERVED, CONFIRMED, CANCELLED);
            case INVENTORY_RESERVED -> Set.of(CONFIRMED, CANCELLED);
            case CONFIRMED, CANCELLED -> Set.of();
        };
    }
}
