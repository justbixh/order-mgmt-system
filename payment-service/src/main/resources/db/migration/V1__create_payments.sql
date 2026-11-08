-- Payment Service schema. The outbox/processed_events tables come from the shared V0_1 migration.
CREATE TABLE payments (
    id                UUID           PRIMARY KEY,
    order_id          UUID           NOT NULL UNIQUE,   -- payment idempotency: one payment per order
    customer_id       VARCHAR(100),
    amount            NUMERIC(19, 2),                   -- nullable: CANCELLED placeholders have no amount
    currency          VARCHAR(3),
    payment_method    VARCHAR(100),
    status            VARCHAR(20)    NOT NULL,
    failure_reason    VARCHAR(500),
    gateway_reference VARCHAR(100),
    created_at        TIMESTAMPTZ    NOT NULL,
    updated_at        TIMESTAMPTZ    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0
);
