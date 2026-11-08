package com.example.ordermgmt.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the outbox publisher, bound from {@code outbox.*} in {@code application.yml}.
 *
 * @param pollIntervalMs how long to wait between two polls of the outbox table
 * @param batchSize      maximum number of rows published per poll
 * @param sendTimeoutMs  how long to wait for the broker to acknowledge one record
 */
@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(Long pollIntervalMs, Integer batchSize, Long sendTimeoutMs) {

    /** Applies defaults for values left out of the configuration. */
    public OutboxProperties {
        pollIntervalMs = pollIntervalMs != null ? pollIntervalMs : 500L;
        batchSize = batchSize != null ? batchSize : 50;
        sendTimeoutMs = sendTimeoutMs != null ? sendTimeoutMs : 5000L;
    }
}
