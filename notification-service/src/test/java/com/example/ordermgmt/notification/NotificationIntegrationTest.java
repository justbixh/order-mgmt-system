package com.example.ordermgmt.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import com.example.ordermgmt.messaging.EventSerde;
import com.example.ordermgmt.testing.ContainersConfig;
import com.example.ordermgmt.testing.KafkaTestClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

/** Real PostgreSQL + Kafka: an OrderConfirmed event produces exactly one notification. */
@SpringBootTest
@Import(ContainersConfig.class)
class NotificationIntegrationTest {

    @Autowired KafkaContainer kafka;
    @Autowired EventSerde serde;
    @Autowired NotificationRepository notifications;

    @Test
    void orderConfirmedProducesOneNotificationEvenWhenDeliveredTwice() {
        UUID orderId = UUID.randomUUID();
        var envelope = EventEnvelope.wrap(
                new OrderConfirmedEvent(orderId, "c-1", "a@b.c", new BigDecimal("10.00"), "USD"));
        var client = new KafkaTestClient(kafka.getBootstrapServers());

        client.send(Topics.ORDER_EVENTS, envelope.aggregateId(), serde.serialize(envelope));
        client.send(Topics.ORDER_EVENTS, envelope.aggregateId(), serde.serialize(envelope));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(notifications.findByOrderIdOrderByCreatedAt(orderId)).hasSize(1));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notifications.findByOrderIdOrderByCreatedAt(orderId)).hasSize(1));
    }
}
