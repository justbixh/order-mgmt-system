package com.example.ordermgmt.contract.events;


import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Inventory Service when stock could not be reserved (unknown product or not
 * enough quantity). No stock was changed. The Order Service cancels the order.
 *
 * @param orderId the order
 * @param reason  why the reservation failed
 */
public record InventoryReservationFailedEvent(UUID orderId, String reason) implements EventPayload {
}
