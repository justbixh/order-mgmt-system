package com.example.ordermgmt.contract.events;


import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Inventory Service when a reservation was not confirmed by a payment in time
 * and the stock was released automatically. The Order Service cancels the order.
 *
 * @param orderId the order
 * @param reason  why it expired
 */
public record InventoryReservationExpiredEvent(UUID orderId, String reason) implements EventPayload {
}
