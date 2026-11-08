-- Order Service schema. The outbox/processed_events tables come from the shared V0_1 migration.
CREATE TABLE orders (
    id              UUID           PRIMARY KEY,
    customer_id     VARCHAR(100)   NOT NULL,
    customer_email  VARCHAR(255)   NOT NULL,
    status          VARCHAR(30)    NOT NULL,
    total_amount    NUMERIC(19, 2) NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    payment_method  VARCHAR(100)   NOT NULL,
    failure_reason  VARCHAR(500),
    idempotency_key VARCHAR(100)   UNIQUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    version         BIGINT         NOT NULL
);
CREATE INDEX idx_orders_customer ON orders (customer_id, created_at DESC);

CREATE TABLE order_items (
    order_id   UUID           NOT NULL REFERENCES orders (id),
    product_id VARCHAR(100)   NOT NULL,
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 2) NOT NULL
);
CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Audit trail of status changes, exposed through the API so the saga is observable.
CREATE TABLE order_status_history (
    order_id   UUID         NOT NULL REFERENCES orders (id),
    status     VARCHAR(30)  NOT NULL,
    reason     VARCHAR(500),
    changed_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_order_history_order ON order_status_history (order_id);
