package com.example.ordermgmt.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderConfirmedEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private final NotificationService service = new NotificationService(repo, sender);

    @Test
    void confirmationIsSentAndRecorded() {
        UUID orderId = UUID.randomUUID();
        service.onOrderConfirmed(new OrderConfirmedEvent(orderId, "c-1", "a@b.c", new BigDecimal("10.00"), "USD"));

        verify(sender).send(eq("a@b.c"), eq("Your order is confirmed"), anyString());
        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(captor.getValue().getOrderId()).isEqualTo(orderId);
    }

    @Test
    void cancellationMentionsReason() {
        service.onOrderCancelled(new OrderCancelledEvent(UUID.randomUUID(), "c-1", "a@b.c", "Payment failed: Card declined"));

        var captor = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("ORDER_CANCELLED");
        assertThat(captor.getValue().getBody()).contains("Card declined");
    }
}
