package com.example.ordermgmt.messaging;

import com.example.ordermgmt.contract.Topics;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring Boot auto-configuration that gives every service the event-driven building blocks
 * (outbox, publisher, idempotent consumer, retry/dead-letter handling, topic declarations) just by
 * having this module on the classpath. Registered in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * <p>Services that never publish events (Notification) still get the outbox beans; they simply
 * have nothing to publish. Keeping one configuration for everyone keeps the services uniform.
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    EventSerde eventSerde(JsonMapper mapper) {
        return new EventSerde(mapper);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    @Bean
    OutboxRepository outboxRepository(JdbcClient jdbcClient) {
        return new OutboxRepository(jdbcClient);
    }

    @Bean
    OutboxService outboxService(OutboxRepository repository, EventSerde serde, ObjectProvider<Tracer> tracer) {
        return new OutboxService(repository, serde, tracer);
    }

    @Bean
    OutboxPublisher outboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                                    TransactionTemplate transaction, OutboxProperties properties) {
        return new OutboxPublisher(repository, kafka, transaction, properties);
    }

    @Bean
    IdempotentEventProcessor idempotentEventProcessor(JdbcClient jdbcClient, TransactionTemplate transaction) {
        return new IdempotentEventProcessor(jdbcClient, transaction);
    }

    @Bean
    EventDispatcher eventDispatcher(EventSerde serde, IdempotentEventProcessor idempotent) {
        return new EventDispatcher(serde, idempotent);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template,
                                          @Value("${messaging.retry.max-retries:4}") int maxRetries,
                                          @Value("${messaging.retry.initial-interval-ms:1000}") long initialMs,
                                          @Value("${messaging.retry.multiplier:2.0}") double multiplier,
                                          @Value("${messaging.retry.max-interval-ms:10000}") long maxMs) {
        return KafkaErrorHandlingConfig.errorHandler(template, maxRetries, initialMs, multiplier, maxMs);
    }

    // ---- topics: created on startup by Spring's KafkaAdmin if missing -----------------------------

    @Bean
    NewTopic orderEventsTopic(@Value("${messaging.topics.partitions:3}") int partitions) {
        return KafkaErrorHandlingConfig.topic(Topics.ORDER_EVENTS, partitions);
    }

    @Bean
    NewTopic inventoryEventsTopic(@Value("${messaging.topics.partitions:3}") int partitions) {
        return KafkaErrorHandlingConfig.topic(Topics.INVENTORY_EVENTS, partitions);
    }

    @Bean
    NewTopic paymentEventsTopic(@Value("${messaging.topics.partitions:3}") int partitions) {
        return KafkaErrorHandlingConfig.topic(Topics.PAYMENT_EVENTS, partitions);
    }

    @Bean
    NewTopic orderEventsDlt() {
        return KafkaErrorHandlingConfig.topic(Topics.ORDER_EVENTS + Topics.DEAD_LETTER_SUFFIX, 1);
    }

    @Bean
    NewTopic inventoryEventsDlt() {
        return KafkaErrorHandlingConfig.topic(Topics.INVENTORY_EVENTS + Topics.DEAD_LETTER_SUFFIX, 1);
    }

    @Bean
    NewTopic paymentEventsDlt() {
        return KafkaErrorHandlingConfig.topic(Topics.PAYMENT_EVENTS + Topics.DEAD_LETTER_SUFFIX, 1);
    }
}
