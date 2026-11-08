package com.example.ordermgmt.inventory;

import com.example.ordermgmt.contract.events.InventoryReleasedEvent;
import com.example.ordermgmt.contract.events.InventoryReservationExpiredEvent;
import com.example.ordermgmt.contract.events.InventoryReservationFailedEvent;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.OrderLine;
import com.example.ordermgmt.inventory.domain.Reservation;
import com.example.ordermgmt.inventory.domain.ReservationLine;
import com.example.ordermgmt.inventory.domain.ReservationRepository;
import com.example.ordermgmt.inventory.domain.ReservationStatus;
import com.example.ordermgmt.inventory.domain.StockItem;
import com.example.ordermgmt.inventory.domain.StockItemRepository;
import com.example.ordermgmt.messaging.OutboxService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic of the Inventory Service: reserve, commit, release and expire reservations.
 *
 * <p>All methods are called inside a database transaction (either opened by the idempotent event
 * processor or by {@code @Transactional} here), so stock changes and the events announcing them
 * are always committed together via the outbox.
 */
@Service
public class ReservationService {

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final StockItemRepository stock;
    private final ReservationRepository reservations;
    private final OutboxService outbox;
    private final Duration reservationTtl;

    public ReservationService(StockItemRepository stock, ReservationRepository reservations, OutboxService outbox,
                              @Value("${inventory.reservation-ttl:2m}") Duration reservationTtl) {
        this.stock = stock;
        this.reservations = reservations;
        this.outbox = outbox;
        this.reservationTtl = reservationTtl;
    }

    /**
     * Tries to reserve stock for every line of the order. <strong>All or nothing</strong>: if any
     * line cannot be satisfied nothing is reserved.
     *
     * <p>Outcome is announced as {@code InventoryReserved} or {@code InventoryReservationFailed}.
     * A business failure ("out of stock") is a normal outcome, not an exception: the event is
     * published and the saga continues.
     *
     * @param order the {@code OrderCreated} payload
     */
    @Transactional
    public void reserve(OrderCreatedEvent order) {
        if (reservations.findByOrderId(order.orderId()).isPresent()) {
            log.info("Reservation for order {} already exists, ignoring", order.orderId());
            return;
        }

        // Merge duplicate lines of the same product so the check below sees the real total.
        Map<String, Integer> wanted = order.items().stream()
                .collect(Collectors.toMap(OrderLine::productId, OrderLine::quantity, Integer::sum, LinkedHashMap::new));

        Map<String, StockItem> locked = stock.findAllForUpdate(wanted.keySet()).stream()
                .collect(Collectors.toMap(StockItem::getProductId, Function.identity()));

        // Pass 1: check every line WITHOUT changing anything, so a failure leaves stock untouched.
        for (var line : wanted.entrySet()) {
            StockItem item = locked.get(line.getKey());
            String problem = item == null
                    ? "Unknown product " + line.getKey()
                    : item.getAvailableQuantity() < line.getValue()
                            ? "Insufficient stock for %s: requested %d, available %d"
                                    .formatted(line.getKey(), line.getValue(), item.getAvailableQuantity())
                            : null;
            if (problem != null) {
                log.warn("Cannot reserve stock for order {}: {}", order.orderId(), problem);
                outbox.publish(new InventoryReservationFailedEvent(order.orderId(), problem));
                return;
            }
        }
        // Pass 2: everything fits, now reserve. The rows are locked, so nobody can take the units meanwhile.
        wanted.forEach((productId, quantity) -> locked.get(productId).reserve(quantity));

        List<ReservationLine> lines = wanted.entrySet().stream()
                .map(e -> new ReservationLine(e.getKey(), e.getValue())).toList();
        reservations.save(new Reservation(order.orderId(), lines));
        outbox.publish(new InventoryReservedEvent(order.orderId(), order.customerId(), order.totalAmount(),
                order.currency(), order.paymentMethod()));
        log.info("Reserved stock for order {}: {}", order.orderId(), wanted);
    }

    /**
     * Payment succeeded: the held units are now sold. Ignored if the reservation is no longer
     * {@code RESERVED} (for example it was already released because the order was cancelled).
     */
    @Transactional
    public void commit(UUID orderId) {
        Reservation reservation = reservations.findByOrderId(orderId).orElse(null);
        if (reservation == null || reservation.getStatus() != ReservationStatus.RESERVED) {
            log.info("Nothing to commit for order {} (reservation {})", orderId,
                    reservation == null ? "missing" : reservation.getStatus());
            return;
        }
        for (StockItem item : lockStock(reservation)) {
            item.commit(quantityOf(reservation, item.getProductId()));
        }
        reservation.markCommitted();
        log.info("Stock committed (sold) for order {}", orderId);
    }

    /**
     * <strong>Compensating transaction.</strong> The order was cancelled, so give its stock back.
     *
     * <ul>
     *   <li>reservation {@code RESERVED}: move units from reserved back to available;</li>
     *   <li>reservation {@code COMMITTED}: the sale is undone, add units back to available;</li>
     *   <li>no reservation or already {@code RELEASED}: nothing to do (idempotent).</li>
     * </ul>
     *
     * @param orderId the cancelled order
     * @param reason  why, for the {@code InventoryReleased} event
     */
    @Transactional
    public void release(UUID orderId, String reason) {
        Reservation reservation = reservations.findByOrderId(orderId).orElse(null);
        if (reservation == null || reservation.getStatus() == ReservationStatus.RELEASED) {
            log.info("Nothing to release for order {} (reservation {})", orderId,
                    reservation == null ? "missing" : "already released");
            return;
        }
        giveBack(reservation);
        outbox.publish(new InventoryReleasedEvent(orderId, reason));
        log.info("Stock released for order {}: {}", orderId, reason);
    }

    /**
     * Releases reservations that never got a payment result within the TTL, so abandoned orders do
     * not hold stock forever, and tells the Order Service to cancel them.
     *
     * @return how many reservations expired
     */
    @Transactional
    public int expireStaleReservations() {
        List<Reservation> stale = reservations.findExpiredForUpdate(Instant.now().minus(reservationTtl));
        for (Reservation reservation : stale) {
            giveBack(reservation);
            outbox.publish(new InventoryReservationExpiredEvent(reservation.getOrderId(),
                    "Reservation expired after " + reservationTtl + " without payment"));
            log.warn("Reservation for order {} expired, stock released", reservation.getOrderId());
        }
        return stale.size();
    }

    private void giveBack(Reservation reservation) {
        boolean wasSold = reservation.getStatus() == ReservationStatus.COMMITTED;
        for (StockItem item : lockStock(reservation)) {
            int qty = quantityOf(reservation, item.getProductId());
            if (wasSold) {
                item.restock(qty);
            } else {
                item.release(qty);
            }
        }
        reservation.markReleased();
    }

    private List<StockItem> lockStock(Reservation reservation) {
        return stock.findAllForUpdate(reservation.getLines().stream().map(ReservationLine::getProductId).toList());
    }

    private int quantityOf(Reservation reservation, String productId) {
        return reservation.getLines().stream()
                .filter(l -> l.getProductId().equals(productId)).mapToInt(ReservationLine::getQuantity).sum();
    }
}
