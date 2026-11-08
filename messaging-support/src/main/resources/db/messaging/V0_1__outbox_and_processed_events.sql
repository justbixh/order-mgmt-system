-- Tables required by the event-driven plumbing (messaging-support).
-- Every service applies this migration to ITS OWN database. Service-specific migrations use V1, V2, ...

-- Transactional outbox: events waiting to be published to Kafka.
-- Rows are inserted in the same transaction as the business change and published later by OutboxPublisher.
CREATE TABLE outbox_events (
    id           BIGSERIAL    PRIMARY KEY,              -- defines publishing order
    event_id     UUID         NOT NULL UNIQUE,          -- event id from the envelope
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL,                 -- Kafka key = order id
    event_type   VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,                 -- full envelope as JSON
    traceparent  VARCHAR(100),                          -- W3C trace context for distributed tracing
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ                            -- NULL until Kafka acknowledged the record
);

-- The publisher only ever looks for unpublished rows, so index only those (keeps the index tiny).
CREATE INDEX idx_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;

-- Idempotent consumer: ids of events this service already handled.
CREATE TABLE processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
