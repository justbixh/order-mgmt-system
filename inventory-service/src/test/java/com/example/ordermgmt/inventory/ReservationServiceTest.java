package com.example.ordermgmt.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.events.InventoryReleasedEvent;
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
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReservationServiceTest {

    private final StockItemRepository stock = mock(StockItemRepository.class);
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final ReservationService service = new ReservationService(stock, reservations, outbox, Duration.ofMinutes(2));

    private StockItem laptop;
    private StockItem phone;

    @BeforeEach
    void setUp() {
        laptop = new StockItem("LAPTOP", "Laptop", 5);
        phone = new StockItem("PHONE", "Phone", 1);
        when(stock.findAllForUpdate(anyCollection())).thenAnswer(inv -> {
            var ids = (java.util.Collection<String>) inv.getArgument(0);
            return List.of(laptop, phone).stream().filter(s -> ids.contains(s.getProductId())).toList();
        });
        when(reservations.findByOrderId(any())).thenReturn(Optional.empty());
    }

    private OrderCreatedEvent order(OrderLine... lines) {
        return new OrderCreatedEvent(UUID.randomUUID(), "c-1", "a@b.c", List.of(lines), BigDecimal.TEN, "USD", "tok_success");
    }

    private EventPayload published() {
        var captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(outbox).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void reservesStockAndPublishesInventoryReserved() {
        service.reserve(order(new OrderLine("LAPTOP", 2, BigDecimal.ONE)));

        assertThat(laptop.getAvailableQuantity()).isEqualTo(3);
        assertThat(laptop.getReservedQuantity()).isEqualTo(2);
        verify(reservations).save(any(Reservation.class));
        assertThat(published()).isInstanceOf(InventoryReservedEvent.class);
    }

    @Test
    void failsAllOrNothingWhenOneLineIsShort() {
        service.reserve(order(new OrderLine("LAPTOP", 2, BigDecimal.ONE), new OrderLine("PHONE", 3, BigDecimal.ONE)));

        assertThat(laptop.getAvailableQuantity()).as("first line must not stay reserved").isEqualTo(5);
        assertThat(phone.getAvailableQuantity()).isEqualTo(1);
        verify(reservations, never()).save(any());
        assertThat(published()).isInstanceOf(InventoryReservationFailedEvent.class);
    }

    @Test
    void unknownProductFails() {
        service.reserve(order(new OrderLine("NOPE", 1, BigDecimal.ONE)));

        assertThat(published()).isInstanceOf(InventoryReservationFailedEvent.class);
    }

    @Test
    void duplicateLinesOfSameProductAreSummed() {
        service.reserve(order(new OrderLine("PHONE", 1, BigDecimal.ONE), new OrderLine("PHONE", 1, BigDecimal.ONE)));

        assertThat(published()).isInstanceOf(InventoryReservationFailedEvent.class);
        assertThat(phone.getAvailableQuantity()).isEqualTo(1);
    }

    @Test
    void existingReservationIsIgnored() {
        var event = order(new OrderLine("LAPTOP", 1, BigDecimal.ONE));
        when(reservations.findByOrderId(event.orderId()))
                .thenReturn(Optional.of(new Reservation(event.orderId(), List.of(new ReservationLine("LAPTOP", 1)))));

        service.reserve(event);

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        verify(outbox, never()).publish(any());
    }

    @Test
    void releaseReturnsReservedStock() {
        laptop.reserve(2);
        UUID orderId = UUID.randomUUID();
        var reservation = new Reservation(orderId, List.of(new ReservationLine("LAPTOP", 2)));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reservation));

        service.release(orderId, "Payment failed");

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        assertThat(laptop.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        assertThat(published()).isInstanceOf(InventoryReleasedEvent.class);
    }

    @Test
    void releaseTwiceOrWithoutReservationIsHarmless() {
        UUID orderId = UUID.randomUUID();
        service.release(orderId, "no reservation");
        verify(outbox, never()).publish(any());

        var released = new Reservation(orderId, List.of(new ReservationLine("LAPTOP", 1)));
        released.markReleased();
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(released));
        service.release(orderId, "again");
        verify(outbox, never()).publish(any());
        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
    }

    @Test
    void commitSellsReservedStock() {
        laptop.reserve(2);
        UUID orderId = UUID.randomUUID();
        var reservation = new Reservation(orderId, List.of(new ReservationLine("LAPTOP", 2)));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reservation));

        service.commit(orderId);

        assertThat(laptop.getReservedQuantity()).isZero();
        assertThat(laptop.getAvailableQuantity()).isEqualTo(3);
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
    }

    @Test
    void releaseAfterCommitRestocksSoldUnits() {
        laptop.reserve(2);
        UUID orderId = UUID.randomUUID();
        var reservation = new Reservation(orderId, List.of(new ReservationLine("LAPTOP", 2)));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        service.commit(orderId);

        service.release(orderId, "cancelled after payment");

        assertThat(laptop.getAvailableQuantity()).isEqualTo(5);
        assertThat(laptop.getReservedQuantity()).isZero();
    }

    @Test
    void commitIgnoredAfterRelease() {
        UUID orderId = UUID.randomUUID();
        var reservation = new Reservation(orderId, List.of(new ReservationLine("LAPTOP", 2)));
        reservation.markReleased();
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reservation));

        service.commit(orderId);

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
    }
}
