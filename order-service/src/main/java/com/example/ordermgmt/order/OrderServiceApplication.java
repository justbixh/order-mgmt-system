package com.example.ordermgmt.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Order Service: the customer-facing entry point of the system.
 *
 * <p>It owns the {@code orders} database, exposes the REST API, starts the saga by publishing
 * {@code OrderCreated}, and finishes it by confirming or cancelling the order depending on the
 * events it receives from the Inventory and Payment services.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
