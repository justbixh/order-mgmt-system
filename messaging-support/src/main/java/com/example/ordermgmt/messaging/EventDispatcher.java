package com.example.ordermgmt.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import java.util.Optional;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * The standard processing pipeline for one incoming Kafka message, shared by all consumers:
 * <ol>
 *   <li>parse the JSON into an {@link EventEnvelope};</li>
 *   <li>put {@code orderId}, {@code eventId} and {@code eventType} into the logging context (MDC),
 *       so every log line written while handling the event carries them;</li>
 *   <li>run the handler through the {@link IdempotentEventProcessor}, so duplicates are skipped.</li>
 * </ol>
 * Offset acknowledgement is deliberately <em>not</em> done here: the listener calls
 * {@code ack.acknowledge()} after this method returns normally, which makes the manual
 * acknowledgement visible in the consumer code.
 */
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final EventSerde serde;
    private final IdempotentEventProcessor idempotent;

    /**
     * @param serde      JSON conversion
     * @param idempotent duplicate protection
     */
    public EventDispatcher(EventSerde serde, IdempotentEventProcessor idempotent) {
        this.serde = serde;
        this.idempotent = idempotent;
    }

    /**
     * Handles one raw Kafka message.
     *
     * @param rawJson the record value
     * @param handler business logic; runs at most once per event id, inside a DB transaction
     * @throws MalformedEventException if the message is not a valid event (goes to the dead-letter topic)
     * @throws RuntimeException        whatever the handler throws (triggers retry, then dead-letter)
     */
    public void dispatch(String rawJson, Consumer<EventEnvelope<? extends EventPayload>> handler) {
        Optional<EventEnvelope<? extends EventPayload>> parsed = serde.deserialize(rawJson);
        if (parsed.isEmpty()) {
            log.warn("Skipping event of unknown type '{}'", serde.peekEventType(rawJson));
            return;
        }
        EventEnvelope<? extends EventPayload> event = parsed.get();
        MDC.put("orderId", event.aggregateId());
        MDC.put("eventId", event.eventId().toString());
        MDC.put("eventType", event.eventType());
        try {
            log.info("Received {} eventId={}", event.eventType(), event.eventId());
            boolean processed = idempotent.processOnce(event.eventId(), event.eventType(), () -> handler.accept(event));
            if (!processed) {
                log.info("Duplicate {} eventId={} ignored (already processed)", event.eventType(), event.eventId());
            }
        } finally {
            MDC.remove("orderId");
            MDC.remove("eventId");
            MDC.remove("eventType");
        }
    }
}
