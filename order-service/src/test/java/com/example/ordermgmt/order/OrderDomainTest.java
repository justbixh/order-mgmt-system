package com.example.ordermgmt.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ordermgmt.order.domain.Order;
import com.example.ordermgmt.order.domain.OrderItem;
import com.example.ordermgmt.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderDomainTest {

    private Order newOrder() {
        return new Order("c-1", "a@b.c",
                List.of(new OrderItem("A", 2, new BigDecimal("10.00")), new OrderItem("B", 1, new BigDecimal("5.50"))),
                "USD", "tok_success", null);
    }

    @Test
    void newOrderIsPendingWithComputedTotalAndHistory() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("25.50");
        assertThat(order.getHistory()).hasSize(1);
    }

    @Test
    void happyPathTransitions() {
        Order order = newOrder();
        order.markInventoryReserved();
        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getHistory()).extracting("status")
                .containsExactly(OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED, OrderStatus.CONFIRMED);
    }

    @Test
    void cancelRecordsReason() {
        Order order = newOrder();
        order.cancel("Payment declined");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).isEqualTo("Payment declined");
    }

    @Test
    void finalStatesCannotChange() {
        Order confirmed = newOrder();
        confirmed.confirm();
        assertThatThrownBy(() -> confirmed.cancel("late")).isInstanceOf(IllegalStateException.class);

        Order cancelled = newOrder();
        cancelled.cancel("x");
        assertThatThrownBy(cancelled::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void terminalFlags() {
        assertThat(OrderStatus.CONFIRMED.isTerminal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
        assertThat(OrderStatus.INVENTORY_RESERVED.isTerminal()).isFalse();
    }
}
