package com.example.ordermgmt.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * SQL access to the {@code outbox_events} table.
 *
 * <p>Plain SQL through {@link JdbcClient} is used on purpose: it joins the transaction that the
 * caller already opened (JPA or JDBC, they share the same connection), and the
 * {@code FOR UPDATE SKIP LOCKED} query is much clearer in SQL than in an ORM.
 */
public class OutboxRepository {

    private final JdbcClient jdbc;

    /** @param jdbc the JDBC client of the service's own database */
    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a new, unpublished outbox row. Must run inside the business transaction.
     */
    public void insert(UUID eventId, String topic, String messageKey, String eventType,
                       String payload, String traceparent) {
        jdbc.sql("""
                INSERT INTO outbox_events (event_id, topic, message_key, event_type, payload, traceparent)
                VALUES (:eventId, :topic, :key, :type, :payload, :traceparent)
                """)
                .param("eventId", eventId)
                .param("topic", topic)
                .param("key", messageKey)
                .param("type", eventType)
                .param("payload", payload)
                .param("traceparent", traceparent)
                .update();
    }

    /**
     * Selects and <em>locks</em> the oldest unpublished rows.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} lets several instances of the same service poll at the
     * same time: a row locked by one instance is simply skipped by the others, so no row is
     * published twice by concurrent pollers.
     *
     * @param limit maximum number of rows
     * @return rows ordered by id (the order they were written)
     */
    public List<OutboxRecord> lockUnpublished(int limit) {
        return jdbc.sql("""
                SELECT id, event_id, topic, message_key, event_type, payload, traceparent
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new OutboxRecord(
                        rs.getLong("id"),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("topic"),
                        rs.getString("message_key"),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getString("traceparent")))
                .list();
    }

    /** Marks a row as published so it is not selected again. */
    public void markPublished(long id) {
        jdbc.sql("UPDATE outbox_events SET published_at = now() WHERE id = :id")
                .param("id", id)
                .update();
    }
}
