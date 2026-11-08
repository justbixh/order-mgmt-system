package com.example.ordermgmt.messaging;

import java.util.UUID;

/**
 * One row of the {@code outbox_events} table: an event waiting to be published to Kafka.
 *
 * @param id          database sequence id; defines the publishing order
 * @param eventId     id of the event inside the payload (also stored for traceability)
 * @param topic       destination topic
 * @param messageKey  Kafka key (the order id)
 * @param eventType   event name, for logging
 * @param payload     the complete {@code EventEnvelope} as JSON
 * @param traceparent W3C trace context captured when the row was written, or {@code null}
 */
public record OutboxRecord(
        long id,
        UUID eventId,
        String topic,
        String messageKey,
        String eventType,
        String payload,
        String traceparent) {
}
