package com.example.ordermgmt.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ordermgmt.contract.EventPayload;
import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import com.example.ordermgmt.contract.events.PaymentRefundedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.messaging.OutboxService;
import com.example.ordermgmt.payment.domain.Payment;
import com.example.ordermgmt.payment.domain.PaymentRepository;
import com.example.ordermgmt.payment.domain.PaymentStatus;
import com.example.ordermgmt.payment.gateway.GatewayUnavailableException;
import com.example.ordermgmt.payment.gateway.MockPaymentGateway;
import com.example.ordermgmt.payment.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class PaymentServiceTest {

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentGateway gateway = Mockito.spy(new MockPaymentGateway());
    private final OutboxService outbox = mock(OutboxService.class);
    private final PaymentService service = new PaymentService(payments, gateway, outbox);

    @BeforeEach
    void setUp() {
        when(payments.findByOrderId(any())).thenReturn(Optional.empty());
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private InventoryReservedEvent reserved(UUID orderId, String token) {
        return new InventoryReservedEvent(orderId, "c-1", new BigDecimal("42.00"), "USD", token);
    }

    private EventPayload published() {
        var captor = ArgumentCaptor.forClass(EventPayload.class);
        verify(outbox).publish(captor.capture());
        return captor.getValue();
    }

    @Test
    void successfulChargePublishesPaymentSucceeded() {
        service.charge(reserved(UUID.randomUUID(), "tok_success"));

        assertThat(published()).isInstanceOf(PaymentSucceededEvent.class);
    }

    @Test
    void declinedChargePublishesPaymentFailedWithReason() {
        service.charge(reserved(UUID.randomUUID(), "tok_declined"));

        assertThat(published()).isInstanceOfSatisfying(PaymentFailedEvent.class,
                e -> assertThat(e.reason()).isEqualTo("Card declined"));
    }

    @Test
    void gatewayOutagePropagatesSoTheEventIsRetried() {
        assertThatThrownBy(() -> service.charge(reserved(UUID.randomUUID(), "tok_gateway_error")))
                .isInstanceOf(GatewayUnavailableException.class);
        verify(outbox, never()).publish(any());
    }

    @Test
    void existingPaymentIsNeverChargedAgain() {
        UUID orderId = UUID.randomUUID();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(
                Payment.completed(orderId, "c-1", BigDecimal.TEN, "USD", "tok_success", "ch_1")));

        service.charge(reserved(orderId, "tok_success"));

        verify(gateway, never()).charge(any(), any(), any(), any());
        verify(outbox, never()).publish(any());
    }

    @Test
    void cancelledBeforeChargeStoresPlaceholderThatBlocksLaterCharge() {
        UUID orderId = UUID.randomUUID();
        service.onOrderCancelled(orderId);

        var captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(captor.getValue()));
        service.charge(reserved(orderId, "tok_success"));
        verify(gateway, never()).charge(any(), any(), any(), any());
    }

    @Test
    void cancelAfterCompletedPaymentRefunds() {
        UUID orderId = UUID.randomUUID();
        Payment paid = Payment.completed(orderId, "c-1", new BigDecimal("42.00"), "USD", "tok_success", "ch_1");
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(paid));

        service.onOrderCancelled(orderId);

        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(gateway).refund("ch_1", new BigDecimal("42.00"));
        assertThat(published()).isInstanceOf(PaymentRefundedEvent.class);
    }

    @Test
    void cancelAfterFailedPaymentDoesNothing() {
        UUID orderId = UUID.randomUUID();
        when(payments.findByOrderId(orderId)).thenReturn(Optional.of(
                Payment.failed(orderId, "c-1", BigDecimal.TEN, "USD", "tok_declined", "Card declined")));

        service.onOrderCancelled(orderId);

        verify(gateway, never()).refund(any(), any());
        verify(outbox, never()).publish(any());
    }
}
