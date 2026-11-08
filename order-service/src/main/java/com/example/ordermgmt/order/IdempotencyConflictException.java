package com.example.ordermgmt.order;

/**
 * Thrown when two requests with the same {@code Idempotency-Key} race and the loser hits the
 * unique constraint. Mapped to HTTP 409; the client should simply retry and will then receive the
 * order created by the winner.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key, Throwable cause) {
        super("A request with Idempotency-Key '" + key + "' is already being processed", cause);
    }
}
