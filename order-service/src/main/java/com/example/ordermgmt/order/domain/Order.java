package com.example.ordermgmt.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The order aggregate. All status changes go through the methods of this class so the lifecycle
 * rules in {@link OrderStatus} cannot be bypassed.
 *
 * <p>{@code version} enables optimistic locking: if two threads (say a customer cancelling and a
 * payment event arriving) modify the same order concurrently, the second commit fails instead of
 * silently overwriting the first.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    @Column(name = "failure_reason")
    private String failureReason;

    /** Client supplied key that makes order creation safe to retry; may be null. */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItem> items = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_status_history", joinColumns = @JoinColumn(name = "order_id"))
    @OrderBy("changedAt ASC")
    private List<OrderStatusChange> history = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
        // required by JPA
    }

    /**
     * Creates a new order in {@link OrderStatus#PENDING}. The id is generated here (not by the
     * database) so it can be put into the outbox event before the row is flushed.
     *
     * @param customerId     who orders
     * @param customerEmail  where notifications go
     * @param items          the lines, at least one
     * @param currency       ISO 4217 code
     * @param paymentMethod  opaque payment token (defaults to the "always succeeds" token)
     * @param idempotencyKey optional client retry key
     */
    public Order(String customerId, String customerEmail, List<OrderItem> items, String currency,
                 String paymentMethod, String idempotencyKey) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.items = new ArrayList<>(items);
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.idempotencyKey = idempotencyKey;
        this.totalAmount = items.stream().map(OrderItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.history.add(new OrderStatusChange(OrderStatus.PENDING, "Order placed", this.createdAt));
    }

    /** Stock was reserved; waiting for payment. */
    public void markInventoryReserved() {
        transitionTo(OrderStatus.INVENTORY_RESERVED, "Stock reserved");
    }

    /** Payment succeeded; the order is complete. */
    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED, "Payment succeeded");
    }

    /**
     * Cancels the order.
     *
     * @param reason why, shown to the customer
     */
    public void cancel(String reason) {
        this.failureReason = reason;
        transitionTo(OrderStatus.CANCELLED, reason);
    }

    private void transitionTo(OrderStatus target, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "Order %s cannot move from %s to %s".formatted(id, status, target));
        }
        this.status = target;
        this.updatedAt = Instant.now();
        this.history.add(new OrderStatusChange(target, reason, this.updatedAt));
    }

    public UUID getId() {
        return id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public List<OrderStatusChange> getHistory() {
        return List.copyOf(history);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
