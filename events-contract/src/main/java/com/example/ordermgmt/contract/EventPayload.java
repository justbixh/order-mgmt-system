package com.example.ordermgmt.contract;

import java.util.UUID;

/**
 * Marker for every event payload that travels through Kafka.
 *
 * <p>All events in this system are about an order, so each payload exposes the {@code orderId}.
 * The record accessor {@code orderId()} satisfies this method automatically. The order id doubles
 * as the Kafka message key (see {@link Topics}).
 */
public interface EventPayload {

    /** The order this event is about. */
    UUID orderId();
}
