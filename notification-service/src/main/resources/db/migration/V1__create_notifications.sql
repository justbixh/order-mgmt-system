-- Notification Service schema. The outbox/processed_events tables come from the shared V0_1 migration.
CREATE TABLE notifications (
    id          UUID          PRIMARY KEY,
    order_id    UUID          NOT NULL,
    customer_id VARCHAR(100)  NOT NULL,
    recipient   VARCHAR(255)  NOT NULL,
    type        VARCHAR(50)   NOT NULL,
    subject     VARCHAR(255)  NOT NULL,
    body        VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    UNIQUE (order_id, type)      -- at most one notification of each kind per order
);
