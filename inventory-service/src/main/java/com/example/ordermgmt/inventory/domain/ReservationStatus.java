package com.example.ordermgmt.inventory.domain;

/** Lifecycle of a stock reservation. */
public enum ReservationStatus {
    /** Stock is held, waiting for the payment result. */
    RESERVED,
    /** Payment succeeded; the stock is sold. */
    COMMITTED,
    /** Stock was given back (order cancelled or reservation expired). */
    RELEASED
}
