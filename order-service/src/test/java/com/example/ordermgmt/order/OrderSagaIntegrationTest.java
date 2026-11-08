package com.example.ordermgmt.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.Topics;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.messaging.EventSerde;
import com.example.ordermgmt.testing.ContainersConfig;
import com.example.ordermgmt.testing.KafkaTestClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.kafka.KafkaContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration test with real PostgreSQL and Kafka (Testcontainers). The other services are
 * simulated by publishing their events by hand, so this test exercises the Order Service's whole
 * event path: REST, database, outbox, publisher, consumer, idempotency.
 */
@SpringBootTest(properties = "outbox.poll-interval-ms=100")
@AutoConfigureMockMvc
@Import(ContainersConfig.class)
class OrderSagaIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired KafkaContainer kafka;
    @Autowired EventSerde serde;
    @Autowired JsonMapper json;

    private static final String BODY = """
            {"customerId":"c-1","customerEmail":"a@b.c","currency":"USD","paymentMethod":"tok_success",
             "items":[{"productId":"SKU-1","quantity":2,"unitPrice":10.00}]}""";

    private UUID placeOrder() throws Exception {
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(20.00))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asString());
    }

    private String orderStatus(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/orders/" + id)).andReturn().getResponse().getContentAsString())
                .get("status").asString();
    }

    private void publish(String topic, EventEnvelope<? extends EventPayload> envelope) {
        new KafkaTestClient(kafka.getBootstrapServers()).send(topic, envelope.aggregateId(), serde.serialize(envelope));
    }

    private long countEvents(String topic, String eventType, UUID orderId, Duration wait) {
        var values = new KafkaTestClient(kafka.getBootstrapServers()).readUntil(topic,
                vs -> vs.stream().filter(v -> v.contains(orderId.toString()) && v.contains(eventType)).count() >= 1, wait);
        return values.stream().filter(v -> v.contains(orderId.toString()) && v.contains("\"" + eventType + "\"")).count();
    }

    @Test
    void creatingAnOrderPublishesOrderCreatedThroughTheOutbox() throws Exception {
        UUID id = placeOrder();

        assertThat(countEvents(Topics.ORDER_EVENTS, "OrderCreated", id, Duration.ofSeconds(20))).isEqualTo(1);
    }

    @Test
    void sagaEventsConfirmTheOrderAndDuplicatesAreIgnored() throws Exception {
        UUID id = placeOrder();

        publish(Topics.INVENTORY_EVENTS,
                EventEnvelope.wrap(new InventoryReservedEvent(id, "c-1", new BigDecimal("20.00"), "USD", "tok_success")));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(orderStatus(id)).isEqualTo("INVENTORY_RESERVED"));

        var paid = EventEnvelope.wrap(new PaymentSucceededEvent(id, UUID.randomUUID(), new BigDecimal("20.00"), "USD"));
        publish(Topics.PAYMENT_EVENTS, paid);
        publish(Topics.PAYMENT_EVENTS, paid); // exact duplicate: same eventId
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(orderStatus(id)).isEqualTo("CONFIRMED"));

        // Exactly one OrderConfirmed despite the duplicate PaymentSucceeded.
        assertThat(countEvents(Topics.ORDER_EVENTS, "OrderConfirmed", id, Duration.ofSeconds(10))).isEqualTo(1);
        JsonNode order = json.readTree(mvc.perform(get("/api/orders/" + id)).andReturn().getResponse().getContentAsString());
        assertThat(order.get("history")).hasSize(3);
    }

    @Test
    void customerCancellationPublishesOrderCancelledAndIsFinal() throws Exception {
        UUID id = placeOrder();

        mvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(post("/api/orders/" + id + "/cancel")).andExpect(status().isConflict());

        assertThat(countEvents(Topics.ORDER_EVENTS, "OrderCancelled", id, Duration.ofSeconds(20))).isEqualTo(1);
    }

    @Test
    void sameIdempotencyKeyCreatesOneOrder() throws Exception {
        var first = mvc.perform(post("/api/orders").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated()).andReturn();
        var second = mvc.perform(post("/api/orders").header("Idempotency-Key", "k-1")
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk()).andReturn();

        assertThat(json.readTree(second.getResponse().getContentAsString()).get("id"))
                .isEqualTo(json.readTree(first.getResponse().getContentAsString()).get("id"));
    }

    @Test
    void invalidRequestIsRejectedWithProblemDetails() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownOrderIs404() throws Exception {
        mvc.perform(get("/api/orders/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }
}
