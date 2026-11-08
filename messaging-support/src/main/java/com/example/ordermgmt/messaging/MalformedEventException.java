package com.example.ordermgmt.messaging;

/**
 * Thrown when a Kafka record cannot be understood at all (invalid JSON, missing envelope fields,
 * unparseable payload).
 *
 * <p>Retrying such a record can never succeed, so the Kafka error handler is configured to send it
 * straight to the dead-letter topic instead of retrying (see {@link KafkaErrorHandlingConfig}).
 */
public class MalformedEventException extends RuntimeException {

    /**
     * @param message what was wrong with the record
     * @param cause   the underlying parsing error
     */
    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
