package com.example.ordermgmt.messaging;

import com.example.ordermgmt.contract.Topics;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.apache.kafka.clients.admin.NewTopic;

/**
 * Factory methods for the Kafka error handling and topic definitions, wired up in
 * {@link MessagingAutoConfiguration}.
 *
 * <h2>What happens when a consumer fails</h2>
 * <ol>
 *   <li>The listener throws. The offset is <em>not</em> acknowledged.</li>
 *   <li>{@link DefaultErrorHandler} waits (exponential backoff: 1s, 2s, 4s, 8s) and re-delivers the
 *       same record. The business transaction was rolled back, so nothing was half-applied.</li>
 *   <li>If all attempts fail, {@link DeadLetterPublishingRecoverer} copies the record to
 *       {@code <topic>.DLT} together with the exception details in headers, and the offset is
 *       committed so the partition is not blocked by one poison message.</li>
 * </ol>
 * Records that can never succeed (see {@link MalformedEventException}) skip the retries.
 */
final class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    private KafkaErrorHandlingConfig() {
    }

    /**
     * Builds the retry + dead-letter error handler. Spring Boot automatically registers a
     * {@code CommonErrorHandler} bean on the default listener container factory.
     *
     * @param template    producer used to write to the dead-letter topics
     * @param maxRetries  redeliveries after the first failure
     * @param initialMs   first backoff interval
     * @param multiplier  growth factor of the interval
     * @param maxMs       upper bound of the interval
     */
    static DefaultErrorHandler errorHandler(KafkaTemplate<String, String> template,
                                            int maxRetries, long initialMs, double multiplier, long maxMs) {
        // Partition -1 = "let the producer choose", so the DLT does not need as many partitions as the source.
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, ex) -> new TopicPartition(record.topic() + Topics.DEAD_LETTER_SUFFIX, -1));

        var backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialMs);
        backOff.setMultiplier(multiplier);
        backOff.setMaxInterval(maxMs);

        var handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Giving up on record {}-{}@{} after retries, sending to dead-letter topic: {}",
                    record.topic(), record.partition(), record.offset(), ex.toString());
            recoverer.accept(record, ex);
        }, backOff);
        // With manual acknowledgement we must tell the container to commit the offset of a record
        // that was handed to the recoverer, otherwise it would be delivered again after a restart.
        handler.setCommitRecovered(true);
        handler.addNotRetryableExceptions(MalformedEventException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("Delivery attempt {} failed for {}-{}@{}: {}",
                        attempt, record.topic(), record.partition(), record.offset(), ex.toString()));
        return handler;
    }

    /** Declares a topic; Spring's {@code KafkaAdmin} creates it at startup if it does not exist. */
    static NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name).partitions(partitions).replicas(1).build();
    }
}
