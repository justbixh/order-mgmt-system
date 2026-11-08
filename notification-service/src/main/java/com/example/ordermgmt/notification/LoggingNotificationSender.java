package com.example.ordermgmt.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Mock channel: "sends" the message by writing it to the log. Swap for a real email provider in production. */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("[mock-email] to={} subject=\"{}\" body=\"{}\"", recipient, subject, body);
    }
}
