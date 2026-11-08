package com.example.ordermgmt.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

/**
 * One entry of an order's audit trail: "the order moved to {@code status} at {@code changedAt}
 * because of {@code reason}". Makes the saga visible through the REST API.
 */
@Embeddable
public class OrderStatusChange {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected OrderStatusChange() {
        // required by JPA
    }

    OrderStatusChange(OrderStatus status, String reason, Instant changedAt) {
        this.status = status;
        this.reason = reason;
        this.changedAt = changedAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
