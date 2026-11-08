package com.example.ordermgmt.contract.events;


import com.example.ordermgmt.contract.EventPayload;
import java.util.UUID;

/**
 * Published by the Inventory Service after it gave reserved stock back (compensation).
 * Informational: nobody has to react to it, but it makes the saga visible in the event log.
 *
 * @param orderId the order
 * @param reason  why the stock was released
 */
public record InventoryReleasedEvent(UUID orderId, String reason) implements EventPayload {
}
