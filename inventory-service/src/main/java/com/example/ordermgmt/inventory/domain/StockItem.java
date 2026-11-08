package com.example.ordermgmt.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stock level of one product, split in two counters:
 * <ul>
 *   <li>{@code availableQuantity}: units that can still be reserved;</li>
 *   <li>{@code reservedQuantity}: units held for orders that are waiting for payment.</li>
 * </ul>
 * Reserving moves units from available to reserved; releasing moves them back; committing (the
 * order was paid) removes them from reserved because they are sold.
 */
@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(nullable = false)
    private String name;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private long version;

    protected StockItem() {
        // required by JPA
    }

    public StockItem(String productId, String name, int availableQuantity) {
        this.productId = productId;
        this.name = name;
        this.availableQuantity = availableQuantity;
    }

    /**
     * Holds {@code quantity} units for an order.
     *
     * @throws InsufficientStockException if not enough units are available
     */
    public void reserve(int quantity) {
        if (availableQuantity < quantity) {
            throw new InsufficientStockException(productId, quantity, availableQuantity);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    /** Gives held units back (the order was cancelled before payment completed). */
    public void release(int quantity) {
        reservedQuantity -= quantity;
        availableQuantity += quantity;
    }

    /** Finalizes the sale of held units (the order was paid). */
    public void commit(int quantity) {
        reservedQuantity -= quantity;
    }

    /** Adds units to the available stock (restock, or return of already sold units). */
    public void restock(int quantity) {
        availableQuantity += quantity;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }
}
