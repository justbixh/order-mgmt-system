package com.example.ordermgmt.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.OrderLine;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventSerdeTest {

    private final EventSerde serde = new EventSerde(JsonMapper.builder().build());

    @Test
    void roundTripPreservesEnvelopeAndPayload() {
        var payload = new OrderCreatedEvent(UUID.randomUUID(), "c-1", "a@b.c",
                List.of(new OrderLine("SKU-1", 2, new BigDecimal("9.99"))), new BigDecimal("19.98"), "USD", "tok_success");
        var envelope = EventEnvelope.wrap(payload);

        var parsed = serde.deserialize(serde.serialize(envelope)).orElseThrow();

        assertThat(parsed.eventId()).isEqualTo(envelope.eventId());
        assertThat(parsed.eventType()).isEqualTo("OrderCreated");
        assertThat(parsed.schemaVersion()).isEqualTo(1);
        assertThat(parsed.aggregateId()).isEqualTo(payload.orderId().toString());
        assertThat(parsed.occurredAt()).isEqualTo(envelope.occurredAt());
        assertThat(parsed.payload()).isEqualTo(payload);
    }

    @Test
    void envelopeUsesExpectedWireNames() {
        String json = serde.serialize(EventEnvelope.wrap(new PaymentFailedEvent(UUID.randomUUID(), UUID.randomUUID(), "declined")));

        assertThat(json).contains("\"eventType\":\"PaymentFailed\"", "\"schemaVersion\":1", "\"eventId\"", "\"occurredAt\"");
    }

    @Test
    void unknownEventTypeIsSkippedNotFailed() {
        String json = """
                {"eventId":"%s","eventType":"SomethingFromTheFuture","schemaVersion":9,
                 "occurredAt":"2026-01-01T00:00:00Z","aggregateId":"x","payload":{}}""".formatted(UUID.randomUUID());

        assertThat(serde.deserialize(json)).isEmpty();
    }

    @Test
    void invalidJsonIsMalformed() {
        assertThatThrownBy(() -> serde.deserialize("not json")).isInstanceOf(MalformedEventException.class);
    }

    @Test
    void missingEnvelopeFieldsAreMalformed() {
        assertThatThrownBy(() -> serde.deserialize("{\"eventType\":\"OrderCreated\",\"payload\":{}}"))
                .isInstanceOf(MalformedEventException.class);
    }
}
