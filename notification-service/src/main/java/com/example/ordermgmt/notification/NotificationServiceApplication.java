package com.example.ordermgmt.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification Service: tells customers how their order ended up.
 *
 * <p>It is a pure <em>consumer</em>: it publishes no events, so its outbox stays empty. That shows
 * that a service can take part in the saga's outcome without being a step in it.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
