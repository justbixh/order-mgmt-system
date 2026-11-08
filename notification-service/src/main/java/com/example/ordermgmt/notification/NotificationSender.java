package com.example.ordermgmt.notification;

/** Port to a delivery channel (email, SMS, push). */
public interface NotificationSender {

    /**
     * Delivers a message.
     *
     * @param recipient address of the customer
     * @param subject   short title
     * @param body      message text
     */
    void send(String recipient, String subject, String body);
}
