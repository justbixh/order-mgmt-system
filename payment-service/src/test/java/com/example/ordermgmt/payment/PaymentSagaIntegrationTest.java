package com.example.ordermgmt.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.messaging.EventSerde;
import com.example.ordermgmt.payment.domain.PaymentRepository;
import com.example.ordermgmt.payment.domain.PaymentStatus;
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

/** Real PostgreSQL + Kafka: charge, decline, refund and idempotency. */
@SpringBootTest(properties = "outbox.poll-interval-ms=100")
@Import(ContainersConfig.class)
class PaymentSagaIntegrationTest {

    @Autowired KafkaContainer kafka;
    @Autowired EventSerde serde;
    @Autowired PaymentRepository payments;

    private void publish(String topic, EventEnvelope<? extends EventPayload> e) {
        new KafkaTestClient(kafka.getBootstrapServers()).send(topic, e.aggregateId(), serde.serialize(e));
    }

    private long countPublished(UUID orderId, String eventType) {
        return new KafkaTestClient(kafka.getBootstrapServers()).readUntil(Topics.PAYMENT_EVENTS,
                vs -> vs.stream().anyMatch(v -> v.contains(orderId.toString()) && v.contains("\"" + eventType + "\"")),
                Duration.ofSeconds(20)).stream()
                .filter(v -> v.contains(orderId.toString()) && v.contains("\"" + eventType + "\"")).count();
    }

    private PaymentStatus statusOf(UUID orderId) {
        return payments.findByOrderId(orderId).map(p -> p.getStatus()).orElse(null);
    }

    private InventoryReservedEvent reserved(String token) {
        return new InventoryReservedEvent(UUID.randomUUID(), "c-1", new BigDecimal("30.00"), "USD", token);
    }

    @Test
    void successfulPaymentThenCancellationRefunds() {
        var reserved = reserved("tok_success");
        publish(Topics.INVENTORY_EVENTS, EventEnvelope.wrap(reserved));
        assertThat(countPublished(reserved.orderId(), "PaymentSucceeded")).isEqualTo(1);
        assertThat(statusOf(reserved.orderId())).isEqualTo(PaymentStatus.COMPLETED);

        publish(Topics.ORDER_EVENTS, EventEnvelope.wrap(
                new OrderCancelledEvent(reserved.orderId(), "c-1", "a@b.c", "Cancelled by customer")));
        assertThat(countPublished(reserved.orderId(), "PaymentRefunded")).isEqualTo(1);
        assertThat(statusOf(reserved.orderId())).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void declinedPaymentPublishesPaymentFailed() {
        var reserved = reserved("tok_declined");
        publish(Topics.INVENTORY_EVENTS, EventEnvelope.wrap(reserved));

        assertThat(countPublished(reserved.orderId(), "PaymentFailed")).isEqualTo(1);
        assertThat(statusOf(reserved.orderId())).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void duplicateAndReplayedRequestsChargeOnce() {
        var reserved = reserved("tok_success");
        var envelope = EventEnvelope.wrap(reserved);
        publish(Topics.INVENTORY_EVENTS, envelope);
        publish(Topics.INVENTORY_EVENTS, envelope);                         // same event id
        publish(Topics.INVENTORY_EVENTS, EventEnvelope.wrap(reserved));     // new event id, same order

        assertThat(countPublished(reserved.orderId(), "PaymentSucceeded")).isEqualTo(1);
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(payments.findAll().stream().filter(p -> p.getOrderId().equals(reserved.orderId()))).hasSize(1));
    }

    @Test
    void cancellationBeforeChargeBlocksTheCharge() {
        var reserved = reserved("tok_success");
        publish(Topics.ORDER_EVENTS, EventEnvelope.wrap(
                new OrderCancelledEvent(reserved.orderId(), "c-1", "a@b.c", "Cancelled by customer")));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(statusOf(reserved.orderId())).isEqualTo(PaymentStatus.CANCELLED));

        publish(Topics.INVENTORY_EVENTS, EventEnvelope.wrap(reserved)); // arrives late

        await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(6)).untilAsserted(() ->
                assertThat(statusOf(reserved.orderId())).isEqualTo(PaymentStatus.CANCELLED));
    }
}
