# Event-Driven Order Management System

A small but realistic e-commerce backend built to **learn and demonstrate event-driven architecture**:
four Spring Boot microservices that never call each other directly. They cooperate only by publishing
and reacting to events on Apache Kafka.

* **Java 21**, **Spring Boot 4.1**, Spring Kafka, Spring Data JPA, Flyway
* **Apache Kafka 4.3** (KRaft, no ZooKeeper), **PostgreSQL 18**
* OpenTelemetry tracing (Jaeger), structured logging, springdoc OpenAPI
* Testcontainers integration tests, plus an end-to-end demo script

> Java 21 is used instead of 17 because it is the current LTS and the Boot 4 baseline recommendation;
> the code uses pattern matching for `switch` (Java 21).

---

## 1. Run it in 3 minutes

Prerequisites: Docker (with Compose v2), `curl` and `jq` for the demo script.

```bash
docker compose up -d --build          # infra + all four services (first build takes a few minutes)
scripts/demo.sh                       # runs every scenario and checks the results
```

| What | URL |
|---|---|
| Order API (Swagger UI) | http://localhost:8081/swagger-ui.html |
| Inventory API | http://localhost:8082/swagger-ui.html |
| Payment API | http://localhost:8083/swagger-ui.html |
| Notification API | http://localhost:8084/swagger-ui.html |
| Kafka UI (topics, messages, DLT) | http://localhost:8090 |
| Jaeger (distributed traces) | http://localhost:16686 |

Place an order by hand:

```bash
curl -s -X POST localhost:8081/api/orders -H 'Content-Type: application/json' -d '{
  "customerId": "cust-1", "customerEmail": "alice@example.com", "currency": "USD",
  "paymentMethod": "tok_success",
  "items": [{"productId": "SKU-HEADPHONES", "quantity": 2, "unitPrice": 49.90}]}' | jq

curl -s localhost:8081/api/orders/<id> | jq     # watch the status history: PENDING -> INVENTORY_RESERVED -> CONFIRMED
```

Run only the infrastructure and start services from your IDE or Maven:

```bash
docker compose up -d postgres kafka kafka-ui jaeger
mvn -DskipTests package
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar      # likewise for the other three
```

Stop everything: `docker compose down` (add `-v` to also wipe the databases).

### Test scenarios (the `paymentMethod` token drives the mock gateway)

| Token | What happens |
|---|---|
| `tok_success` (default) | Order is confirmed, customer notified |
| `tok_declined` / `tok_insufficient_funds` | Payment fails, order cancelled, **stock released** (compensation) |
| `tok_gateway_error` | Gateway outage: the consumer retries with backoff, then the record goes to the **dead-letter topic** |
| `productId: SKU-LIMITED, quantity: 5` | Out of stock (only 1 unit): order cancelled, nothing charged |
| `POST /api/orders/{id}/cancel` | Customer cancellation: stock released, payment refunded if already charged |
| same `Idempotency-Key` header twice | Second call returns the first order, nothing new is created |

Seed products: `SKU-LAPTOP` (10), `SKU-PHONE` (25), `SKU-HEADPHONES` (100), `SKU-KEYBOARD` (50), `SKU-LIMITED` (1).

---

## 2. Architecture

```
            REST                      order-events                       inventory-events                   payment-events
 client ───────────► ORDER ──────────────────────────────► INVENTORY ───────────────────────────► PAYMENT ─────────────────┐
                      ▲  │  OrderCreated                    │   InventoryReserved                  │  PaymentSucceeded       │
                      │  │  OrderConfirmed                  │   InventoryReservationFailed         │  PaymentFailed          │
                      │  │  OrderCancelled                  │   InventoryReservationExpired        │  PaymentRefunded        │
                      │  │                                  │   InventoryReleased                  │                         │
                      │  └────────────────────────────┐     │                                      │                         │
                      └──────── reacts to ────────────┼─────┴──────────────────────────────────────┴─────────────────────────┘
                                                      ▼
                                                NOTIFICATION   (consumes OrderConfirmed / OrderCancelled)

 Each service has its OWN PostgreSQL database:  orders | inventory | payments | notifications
```

### The saga (choreography)

A *saga* is a business transaction spread over several services, each with its own database. There is no
distributed transaction (2PC). Instead each service performs a **local** transaction and publishes an
event; the next service reacts to it. If a step fails, **compensating** actions undo the earlier ones.

This system uses **choreography**: no central coordinator, every service just reacts to events.

**Happy path**
```
Order        saves PENDING order ........................ publishes OrderCreated
Inventory    reserves stock ............................. publishes InventoryReserved
Payment      charges the customer ....................... publishes PaymentSucceeded
Order        marks CONFIRMED ............................ publishes OrderConfirmed
Notification sends the confirmation e-mail
```

**Failure path (payment declined)**
```
Payment      gateway declines ........................... publishes PaymentFailed
Order        marks CANCELLED ............................ publishes OrderCancelled      <- compensation trigger
Inventory    sees OrderCancelled -> releases the stock .. publishes InventoryReleased
Payment      sees OrderCancelled -> nothing to refund (it failed)
Notification sends the cancellation e-mail
```

`OrderCancelled` is the single compensation trigger: whatever the reason (declined payment, out of stock,
expired reservation, customer request), the Order Service decides the order is cancelled, and every
other service undoes its own part when it sees that event.

### Order lifecycle

```
PENDING ──► INVENTORY_RESERVED ──► CONFIRMED
   │                │
   └────────────────┴──────────────► CANCELLED
```
`CONFIRMED` and `CANCELLED` are final. The rules live in `OrderStatus`, and every handler first checks the
current status so late or out-of-order events are ignored safely.

### Event catalogue

All events share one **envelope** (`events-contract/.../EventEnvelope.java`):

```json
{
  "eventId": "6f1c…",             // unique, used for idempotency
  "eventType": "PaymentSucceeded",
  "schemaVersion": 1,             // lets payloads evolve
  "occurredAt": "2026-11-08T14:00:00Z",
  "aggregateId": "b2d9…",         // the order id, also the Kafka message key
  "payload": { "orderId": "b2d9…", "paymentId": "…", "amount": 99.80, "currency": "USD" }
}
```

| Topic (key = orderId) | Producer | Events | Consumers |
|---|---|---|---|
| `order-events` | Order | `OrderCreated`, `OrderConfirmed`, `OrderCancelled` | Inventory, Payment, Notification |
| `inventory-events` | Inventory | `InventoryReserved`, `InventoryReservationFailed`, `InventoryReservationExpired`, `InventoryReleased` | Order, Payment |
| `payment-events` | Payment | `PaymentSucceeded`, `PaymentFailed`, `PaymentRefunded` | Order, Inventory |

Each topic has 3 partitions and a dead-letter topic `<topic>.DLT`.

---

## 3. The patterns, explained

If this is your first event-driven system, read these in order. Each points to the class that implements it.

### Transactional Outbox, `messaging-support/.../OutboxService.java`, `OutboxPublisher.java`
**Problem:** a service must update its database *and* publish an event. Two systems, no shared transaction. If it
saves and then crashes before publishing, the saga silently stalls. If it publishes first and the save fails, the world
sees something that never happened.
**Solution:** write the event to an `outbox_events` table **in the same DB transaction** as the business change. A
background poller (`OutboxPublisher`, every 500 ms) reads unpublished rows, sends them to Kafka, and marks them
published. The state change and the event are atomic.
**Details worth knowing:** the poll query uses `FOR UPDATE SKIP LOCKED` so several instances can poll safely; rows are
sent in id order and the batch stops at the first failure to preserve ordering; delivery is **at least once**.

### Idempotent consumer, `IdempotentEventProcessor.java`
At-least-once delivery means duplicates *will* happen (consumer restart, rebalance, publisher crash). Each service
inserts the event id into `processed_events` **in the same transaction** as its business change, using
`INSERT … ON CONFLICT DO NOTHING`. Nothing inserted means duplicate, so skip. Because both writes commit together, a crash
can never leave "marked processed but not applied".
More layers exist where money is involved: unique `order_id` on `payments`, unique `order_id` on `reservations`, and an
idempotency key passed to the payment gateway.

### Manual acknowledgement and offsets, `*EventListener.java`
Kafka remembers per consumer group how far each partition has been read (the *offset*). With `ack-mode: manual` the
listener calls `ack.acknowledge()` only **after** the database transaction committed. Crash before that and the
message is re-delivered (and deduplicated, see above). Auto-commit is off.

### Retry and dead-letter topics, `KafkaErrorHandlingConfig.java`
If a handler throws, the transaction rolls back and nothing is acknowledged. The error handler re-delivers with
exponential backoff (1 s, 2 s, 4 s, 8 s). After the last retry the record is copied to `<topic>.DLT` with the exception in
headers, and the offset is committed so one bad message cannot block the partition. Unparseable messages skip the
retries (retrying cannot help). Try it with `tok_gateway_error` and look at `inventory-events.DLT` in Kafka UI.

> Business failures (declined card, out of stock) are **not** exceptions. They are normal outcomes that produce
> `PaymentFailed` / `InventoryReservationFailed` events. Exceptions are for technical problems (gateway down, database
> unreachable), which are worth retrying.

### Compensating transactions
Inventory `release()` and Payment `onOrderCancelled()` undo earlier steps. They are idempotent and tolerate any order of
arrival. For instance, if `OrderCancelled` reaches Payment *before* `InventoryReserved` (different topics, no ordering
guarantee), Payment stores a `CANCELLED` placeholder so the late charge request is skipped.

### Ordering
Kafka orders records only within a partition. Every event is keyed by `orderId`, so one order's events from one
service stay in order. Between topics there is no guarantee, which is why handlers are state-tolerant
(see `OrderSagaHandler`).

### Reservation timeout
Inventory releases reservations that got no payment result within `inventory.reservation-ttl` (default 2 min) and
publishes `InventoryReservationExpired`, which makes the Order Service cancel. Abandoned orders never hold stock forever.
Set `INVENTORY_RESERVATION_TTL=20s` to watch it.

### Observability
* **Tracing:** the REST request starts a trace; the trace context is saved in the outbox row and sent as the
  `traceparent` Kafka header; consumers continue the same trace. One order is one `traceId` across all four services
  (spans are exported via OTLP to Jaeger).
* **Logging:** every line shows `[traceId] [order=<orderId>]`. Set `SPRING_PROFILES_ACTIVE=json-logs` for JSON logs.
  Follow one order with `docker compose logs | grep <orderId>`.

---

## 4. Project structure

```
events-contract/      Topics, EventEnvelope, EventType registry, payload records. No dependencies.
messaging-support/    Reusable plumbing used by every service (outbox, publisher, idempotency, error handling, shared config)
order-service/        REST API, order aggregate, saga reactions            :8081  db: orders
inventory-service/    Stock, reservations, expiry job                      :8082  db: inventory
payment-service/      Mock gateway, payments, refunds                      :8083  db: payments
notification-service/ Consumer only: stores and "sends" notifications      :8084  db: notifications
testing-support/      Testcontainers config + tiny Kafka test client (test scope)
infra/                Postgres init script (one database + user per service)
scripts/demo.sh       End-to-end scenarios against the running system
```

Inside a service: domain entities and repositories, a service class with business logic, an `*EventListener` (Kafka in),
a `web` package (REST), and Flyway migrations in `src/main/resources/db/migration`.

**Suggested reading order:** `Topics` → `EventEnvelope` → `OrderService.createOrder` → `OutboxService` →
`OutboxPublisher` → `InventoryEventListener` → `EventDispatcher` → `IdempotentEventProcessor` →
`ReservationService` → `PaymentService` → `OrderSagaHandler`.

---

## 5. Key design decisions

| Decision | Why | Trade-off |
|---|---|---|
| Choreography, not orchestration | No central point of failure, services stay decoupled | The overall flow is implicit; follow it through events (this README, traces) |
| Database per service (one Postgres server, four databases and logins) | Real isolation: a service cannot read another's tables | One server in dev; production would use separate instances |
| Polling outbox, not CDC (Debezium) | Zero extra infrastructure, easy to understand | Small latency (≤ poll interval) and polling load |
| JSON events, shared `events-contract` module | Simple, readable in Kafka UI | Services share a library version; production would use Avro/Protobuf and a schema registry |
| Plain JDBC for the outbox, JPA for the domain | Outbox SQL (`SKIP LOCKED`) is clearer in SQL; both join the same transaction | Two data-access styles |
| Shared `messaging-support` library | One audited implementation of the hard parts | Teams must upgrade it together |
| Pessimistic locks on stock rows, optimistic `@Version` elsewhere | No overselling under concurrency; cheap conflict detection for orders | Lock contention on very hot products |
| Gateway outage = exception (retry, then DLT), decline = event | Don't cancel a customer's order because the provider had a bad minute | Needs DLT monitoring/replay |
| Client supplies unit prices | Keeps the demo small | A real system prices from a catalogue service |

### Known limitations / next steps for production
* No authentication/authorization.
* The mock gateway is called inside the DB transaction; at scale, record a `PENDING` payment first and call the
  gateway outside the transaction.
* DLT records must be inspected manually (Kafka UI); there is no replay tool.
* `processed_events` and published `outbox_events` rows are never purged; add a retention job.
* The outbox publisher stops a batch at the first failure (preserves order, but a poison row blocks later rows).
* Single Kafka broker with replication factor 1.
* A payment succeeding at the same instant a reservation expires is possible; the refund path covers it
  (the order is cancelled, so Payment refunds).

---

## 6. Tests

```bash
mvn test -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false   # unit tests only (fast, no Docker)
mvn test            # unit + integration tests (Testcontainers starts Postgres and Kafka, needs Docker)
scripts/demo.sh     # end-to-end against the running system
```

* **Unit tests:** order state machine, saga handler decisions (including out-of-order and late events), inventory
  all-or-nothing reservation, release/commit, payment charge/decline/refund/idempotency, mock gateway, event serialization.
* **Integration tests (real Kafka + PostgreSQL):** each service receives events and produces the expected events and
  database state, including duplicate delivery, replayed requests, and cancellation racing a charge. The Order
  Service test also drives the REST API, the outbox and the idempotency key.
* **End-to-end:** `scripts/demo.sh` runs happy path, declined payment with compensation, out of stock, customer
  cancellation, idempotency key and the gateway-outage dead-letter path across all four running services.

Run one test: `mvn -pl inventory-service test -Dtest=ReservationServiceTest`.

## 7. Configuration

Every service reads environment variables with sensible local defaults (`application.yml`); shared Kafka, outbox and logging
defaults live in `messaging-support/src/main/resources/messaging-defaults.yml`.

| Variable | Default | Meaning |
|---|---|---|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka address (`kafka:29092` inside Compose) |
| `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, service name, service name | Database connection |
| `OUTBOX_POLL_INTERVAL_MS` | `500` | Outbox polling interval |
| `INVENTORY_RESERVATION_TTL` | `2m` | Unpaid reservations expire after this |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces` | Trace exporter (Jaeger) |
| `SPRING_PROFILES_ACTIVE` | | `json-logs` for JSON logging |

Database credentials in this repo are demo values for local use only.
