package com.example.ordermgmt.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.OrderLine;
import com.example.ordermgmt.inventory.domain.StockItemRepository;
import com.example.ordermgmt.messaging.EventSerde;
import com.example.ordermgmt.testing.ContainersConfig;
import com.example.ordermgmt.testing.KafkaTestClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.kafka.KafkaContainer;

/** Real PostgreSQL + Kafka: events in, stock changes and events out. */
@SpringBootTest(properties = "outbox.poll-interval-ms=100")
@Import(ContainersConfig.class)
class InventorySagaIntegrationTest {

    @Autowired KafkaContainer kafka;
    @Autowired EventSerde serde;
    @Autowired StockItemRepository stock;

    private void publish(String topic, EventEnvelope<? extends EventPayload> e) {
        new KafkaTestClient(kafka.getBootstrapServers()).send(topic, e.aggregateId(), serde.serialize(e));
    }

    private boolean published(UUID orderId, String eventType) {
        return new KafkaTestClient(kafka.getBootstrapServers()).readUntil(Topics.INVENTORY_EVENTS,
                vs -> vs.stream().anyMatch(v -> v.contains(orderId.toString()) && v.contains("\"" + eventType + "\"")),
                Duration.ofSeconds(20)).stream()
                .anyMatch(v -> v.contains(orderId.toString()) && v.contains("\"" + eventType + "\""));
    }

    private OrderCreatedEvent order(String product, int qty) {
        return new OrderCreatedEvent(UUID.randomUUID(), "c-1", "a@b.c",
                List.of(new OrderLine(product, qty, BigDecimal.TEN)), BigDecimal.TEN, "USD", "tok_success");
    }

    @Test
    void reservesThenReleasesStockOnCancellation() {
        int before = stock.findById("SKU-LAPTOP").orElseThrow().getAvailableQuantity();
        var created = order("SKU-LAPTOP", 2);

        publish(Topics.ORDER_EVENTS, EventEnvelope.wrap(created));
        assertThat(published(created.orderId(), "InventoryReserved")).isTrue();
        assertThat(stock.findById("SKU-LAPTOP").orElseThrow().getAvailableQuantity()).isEqualTo(before - 2);

        publish(Topics.ORDER_EVENTS, EventEnvelope.wrap(
                new OrderCancelledEvent(created.orderId(), "c-1", "a@b.c", "Payment failed")));
        assertThat(published(created.orderId(), "InventoryReleased")).isTrue();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(stock.findById("SKU-LAPTOP").orElseThrow().getAvailableQuantity()).isEqualTo(before));
    }

    @Test
    void outOfStockPublishesFailureAndLeavesStockUntouched() {
        int before = stock.findById("SKU-LIMITED").orElseThrow().getAvailableQuantity();
        var created = order("SKU-LIMITED", before + 5);

        publish(Topics.ORDER_EVENTS, EventEnvelope.wrap(created));

        assertThat(published(created.orderId(), "InventoryReservationFailed")).isTrue();
        assertThat(stock.findById("SKU-LIMITED").orElseThrow().getAvailableQuantity()).isEqualTo(before);
    }

    @Test
    void duplicateOrderCreatedReservesOnlyOnce() {
        int before = stock.findById("SKU-PHONE").orElseThrow().getAvailableQuantity();
        var envelope = EventEnvelope.wrap(order("SKU-PHONE", 1));

        publish(Topics.ORDER_EVENTS, envelope);
        publish(Topics.ORDER_EVENTS, envelope);

        assertThat(published(((OrderCreatedEvent) envelope.payload()).orderId(), "InventoryReserved")).isTrue();
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(stock.findById("SKU-PHONE").orElseThrow().getAvailableQuantity()).isEqualTo(before - 1));
    }
}
