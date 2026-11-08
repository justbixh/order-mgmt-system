package com.example.ordermgmt.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically releases reservations that were never paid for. Without it, an order whose payment
 * result gets lost would hold stock forever.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService service;

    public ReservationExpiryJob(ReservationService service) {
        this.service = service;
    }

    /** Runs every {@code inventory.expiry-check-interval-ms} (default 10 s). Never throws. */
    @Scheduled(fixedDelayString = "${inventory.expiry-check-interval-ms:10000}")
    public void run() {
        try {
            int expired = service.expireStaleReservations();
            if (expired > 0) {
                log.info("Expired {} stale reservation(s)", expired);
            }
        } catch (RuntimeException e) {
            log.error("Reservation expiry run failed, will retry", e);
        }
    }
}
