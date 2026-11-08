package com.example.ordermgmt.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.EventType;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Entry point of the <strong>Transactional Outbox</strong> pattern for code that wants to publish
 * an event.
 *
 * <h2>The problem</h2>
 * A service must update its database <em>and</em> tell the world about it via Kafka. These are two
 * systems, so there is no atomic way to do both. If the service saves to the database and then the
 * Kafka send fails (or the process crashes in between), the rest of the system never learns about
 * the change, and the saga silently stalls.
 *
 * <h2>The solution</h2>
 * Do not send to Kafka inside the business transaction. Instead, write the event into an
 * {@code outbox_events} table <em>in the same database transaction</em> as the business change.
 * Either both are committed or neither is. A separate background job, the {@link OutboxPublisher},
 * reads the table and sends the rows to Kafka afterwards.
 *
 * <p>The price is that delivery becomes <em>at least once</em> (the publisher may crash after
 * sending but before marking a row as published), which is why every consumer must be idempotent
 * (see {@link IdempotentEventProcessor}).
 */
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxRepository repository;
    private final EventSerde serde;
    private final ObjectProvider<Tracer> tracer;

    /**
     * @param repository outbox SQL access
     * @param serde      JSON conversion
     * @param tracer     optional tracer, used to remember which trace the event belongs to
     */
    public OutboxService(OutboxRepository repository, EventSerde serde, ObjectProvider<Tracer> tracer) {
        this.repository = repository;
        this.serde = serde;
        this.tracer = tracer;
    }

    /**
     * Stores an event in the outbox. The topic and message key are derived from the payload type.
     *
     * <p>Must be called from inside an active transaction; otherwise the whole point of the
     * pattern is lost, so this method fails fast.
     *
     * @param payload the business event to publish
     * @throws IllegalStateException if no transaction is active
     */
    public void publish(EventPayload payload) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "OutboxService.publish must be called inside the business transaction");
        }
        EventEnvelope<EventPayload> envelope = EventEnvelope.wrap(payload);
        EventType type = EventType.forPayload(payload.getClass());
        repository.insert(
                envelope.eventId(),
                type.topic(),
                envelope.aggregateId(),
                envelope.eventType(),
                serde.serialize(envelope),
                currentTraceparent());
        log.info("Outbox: queued {} eventId={} for topic {} (will be published after commit)",
                envelope.eventType(), envelope.eventId(), type.topic());
    }

    /**
     * Builds a W3C {@code traceparent} value from the current span so the trace can continue
     * across the asynchronous hop through the outbox table and Kafka.
     *
     * @return the header value, or {@code null} when there is no active trace
     */
    private String currentTraceparent() {
        Tracer t = tracer.getIfAvailable();
        if (t == null) {
            return null;
        }
        Span span = t.currentSpan();
        if (span == null) {
            return null;
        }
        return "00-" + span.context().traceId() + "-" + span.context().spanId() + "-01";
    }
}
