package com.example.ordermgmt.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment for one order. The unique constraint on {@code order_id} is the <strong>payment
 * idempotency guarantee</strong>: even if two copies of the same request slip past every other
 * check, the database refuses to create a second payment for an order.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id")
    private String customerId;

    private BigDecimal amount;

    private String currency;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "gateway_reference")
    private String gatewayReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Payment() {
        // required by JPA
    }

    private Payment(UUID orderId, String customerId, BigDecimal amount, String currency, String paymentMethod,
                    PaymentStatus status) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** A successful charge. */
    public static Payment completed(UUID orderId, String customerId, BigDecimal amount, String currency,
                                    String paymentMethod, String gatewayReference) {
        Payment p = new Payment(orderId, customerId, amount, currency, paymentMethod, PaymentStatus.COMPLETED);
        p.gatewayReference = gatewayReference;
        return p;
    }

    /** A declined charge. */
    public static Payment failed(UUID orderId, String customerId, BigDecimal amount, String currency,
                                 String paymentMethod, String reason) {
        Payment p = new Payment(orderId, customerId, amount, currency, paymentMethod, PaymentStatus.FAILED);
        p.failureReason = reason;
        return p;
    }

    /** Placeholder for an order that was cancelled before any charge was attempted. */
    public static Payment cancelledBeforeCharge(UUID orderId) {
        return new Payment(orderId, null, null, null, null, PaymentStatus.CANCELLED);
    }

    /** Marks a completed payment as refunded. */
    public void markRefunded() {
        if (status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException("Only a COMPLETED payment can be refunded, was " + status);
        }
        this.status = PaymentStatus.REFUNDED;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
