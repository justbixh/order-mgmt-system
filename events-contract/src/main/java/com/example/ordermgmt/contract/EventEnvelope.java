package com.example.ordermgmt.contract;

import java.time.Instant;
import java.util.UUID;

/**
 * The wrapper around every event on the wire.
 *
 * <p>The envelope carries the metadata a consumer needs <em>before</em> it looks at the business
 * data: a unique {@code eventId} (used for idempotency), the {@code eventType} (used to pick the
 * right payload class), a {@code schemaVersion} (so payloads can evolve) and a timestamp.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "eventId": "6f1c...",
 *   "eventType": "OrderCreated",
 *   "schemaVersion": 1,
 *   "occurredAt": "2026-11-08T14:00:00Z",
 *   "aggregateId": "b2d9...",
 *   "payload": { "orderId": "b2d9...", "customerId": "c-1", "...": "..." }
 * }
 * }</pre>
 *
 * @param eventId       globally unique id of this event; consumers use it to detect duplicates
 * @param eventType     name of the event, see {@link EventType}
 * @param schemaVersion version of the payload layout, starts at 1
 * @param occurredAt    when the event was created (not when it was published to Kafka)
 * @param aggregateId   id of the entity the event is about (the order id); also the Kafka key
 * @param payload       the business data
 * @param <T>           the payload type
 */
public record EventEnvelope<T extends EventPayload>(
        UUID eventId,
        String eventType,
        int schemaVersion,
        Instant occurredAt,
        String aggregateId,
        T payload) {

    /** The schema version written by this build of the system. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Wraps a payload in a fresh envelope with a new event id and the current time.
     *
     * @param payload the business data; its class must be registered in {@link EventType}
     * @return a new envelope
     */
    public static <T extends EventPayload> EventEnvelope<T> wrap(T payload) {
        EventType type = EventType.forPayload(payload.getClass());
        return new EventEnvelope<>(
                UUID.randomUUID(),
                type.eventName(),
                CURRENT_SCHEMA_VERSION,
                Instant.now(),
                payload.orderId().toString(),
                payload);
    }
}
