package com.example.ordermgmt.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Quantity of one product held by a {@link Reservation}. */
@Embeddable
public class ReservationLine {

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    protected ReservationLine() {
        // required by JPA
    }

    public ReservationLine(String productId, int quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
