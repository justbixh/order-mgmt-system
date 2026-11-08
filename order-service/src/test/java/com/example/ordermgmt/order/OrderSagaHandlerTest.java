package com.example.ordermgmt.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ordermgmt.contract.EventEnvelope;
import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.events.InventoryReservationFailedEvent;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.messaging.OutboxService;
import com.example.ordermgmt.order.domain.Order;
import com.example.ordermgmt.order.domain.OrderItem;
import com.example.ordermgmt.order.domain.OrderNotFoundException;
import com.example.ordermgmt.order.domain.OrderRepository;
import com.example.ordermgmt.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OrderSagaHandlerTest {

    private final OrderRepository repo = mock(OrderRepository.class);
    private final OutboxService outbox = mock(OutboxService.class);
    private final OrderSagaHandler handler = new OrderSagaHandler(repo, outbox);
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order("c-1", "a@b.c", List.of(new OrderItem("A", 1, BigDecimal.TEN)), "USD", "tok_success", null);
        when(repo.findById(order.getId())).thenReturn(Optional.of(order));
    }

    private void send(EventPayload payload) {
        handler.handle(EventEnvelope.wrap(payload));
    }

    @Test
    void inventoryReservedMovesPendingOrderForward() {
        send(new InventoryReservedEvent(order.getId(), "c-1", BigDecimal.TEN, "USD", "tok_success"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVED);
        verify(outbox, never()).publish(any());
    }

    @Test
    void paymentSucceededConfirmsOrderAndPublishesOrderConfirmed() {
        send(new PaymentSucceededEvent(order.getId(), UUID.randomUUID(), BigDecimal.TEN, "USD"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        var captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(outbox).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderConfirmedEvent.class);
    }

    @Test
    void paymentFailedCancelsOrderAndPublishesOrderCancelled() {
        send(new PaymentFailedEvent(order.getId(), UUID.randomUUID(), "Card declined"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).contains("Card declined");
        var captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(outbox).publish(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    void inventoryFailureCancelsOrder() {
        send(new InventoryReservationFailedEvent(order.getId(), "Insufficient stock"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void paymentSucceededForCancelledOrderIsIgnored() {
        order.cancel("Cancelled by customer");

        send(new PaymentSucceededEvent(order.getId(), UUID.randomUUID(), BigDecimal.TEN, "USD"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outbox, never()).publish(any());
    }

    @Test
    void lateInventoryReservedDoesNotRegressConfirmedOrder() {
        order.confirm();

        send(new InventoryReservedEvent(order.getId(), "c-1", BigDecimal.TEN, "USD", "tok_success"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentSucceededBeforeInventoryReservedStillConfirms() {
        send(new PaymentSucceededEvent(order.getId(), UUID.randomUUID(), BigDecimal.TEN, "USD"));
        send(new InventoryReservedEvent(order.getId(), "c-1", BigDecimal.TEN, "USD", "tok_success"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void unknownOrderFailsSoItIsRetriedAndDeadLettered() {
        UUID unknown = UUID.randomUUID();
        when(repo.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> send(new PaymentFailedEvent(unknown, UUID.randomUUID(), "x")))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
