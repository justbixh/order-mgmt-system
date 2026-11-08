package com.example.ordermgmt.testing;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a throw-away PostgreSQL and Kafka in Docker for an integration test and wires the
 * application to them automatically ({@code @ServiceConnection} replaces the datasource and Kafka
 * bootstrap properties). Import it with {@code @Import(ContainersConfig.class)}.
 *
 * <p>Requires a running Docker daemon.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:18");
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka:4.3.2");
    }
}
