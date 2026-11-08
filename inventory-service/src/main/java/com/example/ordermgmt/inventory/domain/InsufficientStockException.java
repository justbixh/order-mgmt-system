package com.example.ordermgmt.inventory.domain;

/** Thrown by {@link StockItem#reserve(int)} when fewer units are available than requested. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String productId, int requested, int available) {
        super("Insufficient stock for %s: requested %d, available %d".formatted(productId, requested, available));
    }
}
