# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Event-driven order management: four Spring Boot 4.1 / Java 21 services (order, inventory, payment, notification) coordinated by saga choreography over Kafka, one PostgreSQL database each. The README explains the patterns and design decisions in depth; the user is learning event-driven architecture, so keep Javadoc and "why" comments thorough.

## Commands

```bash
docker compose up -d postgres kafka kafka-ui jaeger   # infra only (to run services from the IDE)
docker compose up -d --build                           # infra + all four services
mvn -DskipTests package                                # build all modules
mvn test -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false   # unit tests only, no Docker
mvn test                                               # unit + integration tests (Testcontainers; Docker must be running)
mvn -pl inventory-service test -Dtest=ReservationServiceTest            # single unit test class
mvn -pl order-service test -Dtest=OrderSagaIntegrationTest             # single integration test
scripts/demo.sh                                        # end-to-end scenarios against running services (needs curl, jq)
```

Integration tests are plain JUnit tests named `*IntegrationTest`; they run in the normal test phase, so `mvn test` also needs Docker.

## Architecture (big picture)

- `events-contract`: the wire contract. `EventType` is the registry mapping each event to its topic and payload class; `EventEnvelope.wrap(payload)` builds the envelope. Adding an event = payload record + one `EventType` line.
- `messaging-support`: auto-configured plumbing used by every service (see `MessagingAutoConfiguration`): `OutboxService` (write event in the business transaction), `OutboxPublisher` (scheduled poll with `FOR UPDATE SKIP LOCKED`, forwards a stored `traceparent` header), `IdempotentEventProcessor` (`processed_events` insert + handler in one transaction), `EventDispatcher` (parse, MDC, idempotency), `KafkaErrorHandlingConfig` (backoff + `.DLT`). Shared defaults come from `messaging-defaults.yml`, imported by each service via `spring.config.import`. Shared tables come from Flyway migration `db/messaging/V0_1__...sql` (service migrations are V1+).
- Each service: a `@KafkaListener` (manual ack, calls `dispatcher.dispatch(...)` then `ack.acknowledge()`), a handler with a pattern-matching `switch` on the payload type, domain logic, REST controllers.
- Saga: Order publishes `OrderCreated` → Inventory `InventoryReserved`/`...Failed` → Payment `PaymentSucceeded`/`PaymentFailed` → Order confirms or cancels. `OrderCancelled` is the single compensation trigger (Inventory releases, Payment refunds). Handlers must tolerate duplicate, late and cross-topic out-of-order events (check current status first).
- Business failures (decline, out of stock) are events; technical failures (gateway outage) are exceptions that go through retry then DLT.

## Gotchas

- Spring Boot 4 / Jackson 3: JSON classes are `tools.jackson.*` (`JsonMapper`, `asString()`), test annotations live in new packages (e.g. `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`).
- Hibernate runs with `ddl-auto: validate`: entity column types must match the Flyway SQL exactly (e.g. use `VARCHAR(3)`, not `CHAR(3)`). Editing an applied migration breaks Flyway checksums on existing databases; reset with `docker compose down -v`.
- The shell used here may have `noclobber` set: use `>|` to overwrite files from scripts.
- Mock payment gateway behaviour is chosen by the order's `paymentMethod` token (`tok_success`, `tok_declined`, `tok_insufficient_funds`, `tok_gateway_error`).
