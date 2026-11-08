package com.example.ordermgmt.inventory.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for {@link Reservation}. */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderId(UUID orderId);

    /** Reservations still waiting for a payment result that are older than {@code cutoff}, locked for update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.status = 'RESERVED' and r.createdAt < :cutoff order by r.createdAt")
    List<Reservation> findExpiredForUpdate(@Param("cutoff") Instant cutoff);
}
