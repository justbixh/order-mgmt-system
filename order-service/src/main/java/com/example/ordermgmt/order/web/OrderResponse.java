package com.example.ordermgmt.order.web;

import com.example.ordermgmt.order.domain.Order;
import com.example.ordermgmt.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representation of an order returned by the API, including its status history. */
public record OrderResponse(
        UUID id,
        String customerId,
        String customerEmail,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String failureReason,
        List<Item> items,
        List<StatusChange> history,
        Instant createdAt,
        Instant updatedAt) {

    /** One order line. */
    public record Item(String productId, int quantity, BigDecimal unitPrice) {
    }

    /** One step of the order's lifecycle. */
    public record StatusChange(OrderStatus status, String reason, Instant changedAt) {
    }

    /** Maps the entity to its API representation. */
    public static OrderResponse from(Order o) {
        return new OrderResponse(
                o.getId(), o.getCustomerId(), o.getCustomerEmail(), o.getStatus(), o.getTotalAmount(),
                o.getCurrency(), o.getFailureReason(),
                o.getItems().stream().map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice())).toList(),
                o.getHistory().stream().map(h -> new StatusChange(h.getStatus(), h.getReason(), h.getChangedAt())).toList(),
                o.getCreatedAt(), o.getUpdatedAt());
    }
}
