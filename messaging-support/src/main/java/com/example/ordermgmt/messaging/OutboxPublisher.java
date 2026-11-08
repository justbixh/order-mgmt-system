package com.example.ordermgmt.messaging;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background job that moves events from the outbox table to Kafka ("polling publisher").
 *
 * <p>Every {@code outbox.poll-interval-ms} it opens a transaction, locks the oldest unpublished
 * rows, sends them to Kafka one by one (waiting for the broker's acknowledgement) and marks each
 * as published.
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li><b>No lost events:</b> a row is only marked published after Kafka acknowledged it.</li>
 *   <li><b>At-least-once:</b> if the process dies between the Kafka acknowledgement and the commit,
 *       the row is published again after restart. Consumers deduplicate by event id.</li>
 *   <li><b>Per-order ordering:</b> rows are sent in id order, and the key is the order id, so a
 *       single order's events reach one partition in the order they were written. The publisher
 *       stops at the first failure to avoid sending later events before an earlier one.</li>
 * </ul>
 */
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final TransactionTemplate transaction;
    private final OutboxProperties properties;

    /**
     * @param repository  outbox SQL access
     * @param kafka       Kafka producer
     * @param transaction transaction boundary for one polling batch
     * @param properties  polling settings
     */
    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                           TransactionTemplate transaction, OutboxProperties properties) {
        this.repository = repository;
        this.kafka = kafka;
        this.transaction = transaction;
        this.properties = properties;
    }

    /**
     * One polling cycle. Scheduled with a fixed <em>delay</em>, so cycles never overlap inside one
     * instance. Never throws: a failing cycle is logged and retried at the next tick.
     */
    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:500}")
    public void poll() {
        try {
            Integer sent = transaction.execute(status -> publishBatch());
            if (sent != null && sent > 0) {
                log.debug("Outbox: published {} event(s)", sent);
            }
        } catch (RuntimeException e) {
            log.error("Outbox: polling cycle failed, will retry", e);
        }
    }

    /** Publishes one batch inside the surrounding transaction and returns how many were sent. */
    private int publishBatch() {
        List<OutboxRecord> batch = repository.lockUnpublished(properties.batchSize());
        int sent = 0;
        for (OutboxRecord row : batch) {
            try {
                send(row);
                repository.markPublished(row.id());
                sent++;
            } catch (Exception e) {
                // Stop here: sending later rows first would reorder events. Rows already sent in this
                // batch are still marked published because we return normally and the transaction commits.
                log.warn("Outbox: could not publish {} eventId={} to {} ({}); will retry",
                        row.eventType(), row.eventId(), row.topic(), e.toString());
                break;
            }
        }
        return sent;
    }

    private void send(OutboxRecord row) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(row.topic(), row.messageKey(), row.payload());
        if (row.traceparent() != null) {
            // Lets the consumer continue the same distributed trace the request started.
            record.headers().add("traceparent", row.traceparent().getBytes(StandardCharsets.UTF_8));
        }
        var result = kafka.send(record).get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
        log.info("Outbox: published {} eventId={} to {}-{}@{}", row.eventType(), row.eventId(),
                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }
}
