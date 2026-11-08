package com.example.ordermgmt.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;

/**
 * One line of an order. Stored in {@code order_items}; has no identity of its own, it is part of
 * the {@link Order} aggregate.
 */
@Embeddable
public class OrderItem {

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    protected OrderItem() {
        // required by JPA
    }

    /**
     * @param productId id of the product
     * @param quantity  units ordered, at least 1
     * @param unitPrice price per unit
     */
    public OrderItem(String productId, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    /** @return {@code unitPrice * quantity} */
    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
