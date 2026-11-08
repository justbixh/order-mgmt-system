package com.example.ordermgmt.messaging;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.EventType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Converts {@link EventEnvelope}s to and from JSON.
 *
 * <p>Reading is done in two steps because the payload class depends on the event type:
 * <ol>
 *   <li>parse the JSON into a tree and read the envelope fields, including {@code eventType};</li>
 *   <li>look up the payload class for that type in {@link EventType} and convert the
 *       {@code payload} sub-tree into it.</li>
 * </ol>
 */
public class EventSerde {

    private final JsonMapper mapper;

    /** @param mapper the application's configured Jackson mapper */
    public EventSerde(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Serializes an envelope to JSON.
     *
     * @param envelope the envelope to write
     * @return JSON text
     */
    public String serialize(EventEnvelope<?> envelope) {
        return mapper.writeValueAsString(envelope);
    }

    /**
     * Parses a JSON event.
     *
     * @param json the raw record value read from Kafka
     * @return the envelope, or empty if the event type is unknown to this build of the service
     *         (callers should log and skip: a newer producer may emit events we do not handle yet)
     * @throws MalformedEventException if the JSON is invalid or does not match the contract
     */
    public Optional<EventEnvelope<? extends EventPayload>> deserialize(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            String eventName = root.path("eventType").asString();
            Optional<EventType> type = EventType.byName(eventName);
            if (type.isEmpty()) {
                return Optional.empty();
            }
            EventPayload payload = mapper.treeToValue(root.path("payload"), type.get().payloadClass());
            return Optional.of(new EventEnvelope<>(
                    UUID.fromString(root.path("eventId").asString()),
                    eventName,
                    root.path("schemaVersion").asInt(),
                    Instant.parse(root.path("occurredAt").asString()),
                    root.path("aggregateId").asString(),
                    payload));
        } catch (RuntimeException e) {
            throw new MalformedEventException("Cannot parse event: " + e.getMessage(), e);
        }
    }

    /**
     * Reads only the {@code eventType} of a JSON event, used for log messages when the full parse
     * is not possible.
     */
    public String peekEventType(String json) {
        try {
            return mapper.readTree(json).path("eventType").asString("?");
        } catch (RuntimeException e) {
            return "?";
        }
    }
}
