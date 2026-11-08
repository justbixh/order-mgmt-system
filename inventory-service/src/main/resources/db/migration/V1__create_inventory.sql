-- Inventory Service schema. The outbox/processed_events tables come from the shared V0_1 migration.
CREATE TABLE stock_items (
    product_id         VARCHAR(100) PRIMARY KEY,
    name               VARCHAR(255) NOT NULL,
    available_quantity INT          NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  INT          NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version            BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE reservations (
    id         UUID        PRIMARY KEY,
    order_id   UUID        NOT NULL UNIQUE,   -- one reservation per order
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0
);
CREATE INDEX idx_reservations_expiry ON reservations (created_at) WHERE status = 'RESERVED';

CREATE TABLE reservation_lines (
    reservation_id UUID         NOT NULL REFERENCES reservations (id),
    product_id     VARCHAR(100) NOT NULL,
    quantity       INT          NOT NULL CHECK (quantity > 0)
);
CREATE INDEX idx_reservation_lines_reservation ON reservation_lines (reservation_id);
