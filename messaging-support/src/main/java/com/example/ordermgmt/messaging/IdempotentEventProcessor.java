package com.example.ordermgmt.messaging;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes event handling <strong>idempotent</strong>: handling the same event twice has the same
 * effect as handling it once.
 *
 * <h2>Why it is needed</h2>
 * Kafka (and our outbox) deliver <em>at least once</em>. The same event can arrive again after a
 * consumer rebalance, a crash before the offset was committed, or an outbox publisher restart.
 * Without protection we might reserve stock twice or charge a customer twice.
 *
 * <h2>How it works</h2>
 * Inside one database transaction the processor first inserts the event id into
 * {@code processed_events}. The insert uses {@code ON CONFLICT DO NOTHING}:
 * <ul>
 *   <li>row inserted: first time we see this event, so run the business handler;</li>
 *   <li>nothing inserted: duplicate, skip the handler.</li>
 * </ul>
 * Because the marker and the business changes commit together, a crash can never leave the event
 * marked as processed without its effects (or the reverse).
 */
public class IdempotentEventProcessor {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;

    /**
     * @param jdbc        JDBC client of the service's own database
     * @param transaction transaction boundary
     */
    public IdempotentEventProcessor(JdbcClient jdbc, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.transaction = transaction;
    }

    /**
     * Runs {@code action} exactly once per event id.
     *
     * @param eventId   id from the event envelope
     * @param eventType event name, stored for debugging
     * @param action    the business handler; if it throws, the transaction rolls back and the event
     *                  is <em>not</em> marked as processed, so a retry will run it again
     * @return {@code true} if the action ran, {@code false} if the event was a duplicate
     */
    public boolean processOnce(UUID eventId, String eventType, Runnable action) {
        Boolean processed = transaction.execute(status -> {
            int inserted = jdbc.sql("""
                    INSERT INTO processed_events (event_id, event_type) VALUES (:id, :type)
                    ON CONFLICT (event_id) DO NOTHING
                    """)
                    .param("id", eventId)
                    .param("type", eventType)
                    .update();
            if (inserted == 0) {
                return false;
            }
            action.run();
            return true;
        });
        return Boolean.TRUE.equals(processed);
    }
}
