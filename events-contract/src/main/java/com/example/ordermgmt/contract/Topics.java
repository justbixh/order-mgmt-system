package com.example.ordermgmt.contract;

/**
 * Names of the Kafka topics used by the system.
 *
 * <p>There is one topic per <em>producing</em> service. A topic carries every event that service
 * publishes, and each event is keyed by {@code orderId}. Kafka only guarantees ordering inside a
 * single partition, and records with the same key always land in the same partition, so all
 * events about one order stay in the order they were published.
 *
 * <p>Every topic also has a dead-letter topic named {@code <topic>}{@value #DEAD_LETTER_SUFFIX},
 * where records that could not be processed after all retries are parked for inspection.
 */
public final class Topics {

    /** Events published by the Order Service. */
    public static final String ORDER_EVENTS = "order-events";

    /** Events published by the Inventory Service. */
    public static final String INVENTORY_EVENTS = "inventory-events";

    /** Events published by the Payment Service. */
    public static final String PAYMENT_EVENTS = "payment-events";

    /** Suffix appended to a topic name to get its dead-letter topic. */
    public static final String DEAD_LETTER_SUFFIX = ".DLT";

    private Topics() {
    }
}
