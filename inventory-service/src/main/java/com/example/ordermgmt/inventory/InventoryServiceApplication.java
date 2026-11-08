package com.example.ordermgmt.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Inventory Service: owns product stock and the reservations made for orders.
 *
 * <p>It reacts to {@code OrderCreated} by reserving stock, to {@code PaymentSucceeded} by turning
 * the reservation into a sale, and to {@code OrderCancelled} by giving the stock back
 * (the compensating transaction).
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
