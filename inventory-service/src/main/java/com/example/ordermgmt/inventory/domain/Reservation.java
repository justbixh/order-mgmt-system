package com.example.ordermgmt.inventory.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Stock held for one order. There is at most one reservation per order (unique {@code order_id}),
 * which is a second line of defence against duplicate {@code OrderCreated} events.
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "reservation_lines", joinColumns = @JoinColumn(name = "reservation_id"))
    private List<ReservationLine> lines = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Reservation() {
        // required by JPA
    }

    /** Creates a reservation in state {@link ReservationStatus#RESERVED}. */
    public Reservation(UUID orderId, List<ReservationLine> lines) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.lines = new ArrayList<>(lines);
        this.status = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markCommitted() {
        changeStatus(ReservationStatus.COMMITTED);
    }

    public void markReleased() {
        changeStatus(ReservationStatus.RELEASED);
    }

    private void changeStatus(ReservationStatus target) {
        this.status = target;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationLine> getLines() {
        return List.copyOf(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
