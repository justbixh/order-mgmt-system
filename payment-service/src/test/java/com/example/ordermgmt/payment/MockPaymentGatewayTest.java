package com.example.ordermgmt.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.ordermgmt.payment.gateway.GatewayUnavailableException;
import com.example.ordermgmt.payment.gateway.MockPaymentGateway;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway();

    @Test
    void successAndUnknownTokensSucceedWithReference() {
        for (String token : new String[] {"tok_success", "anything", null}) {
            var result = gateway.charge("k", token, BigDecimal.TEN, "USD");
            assertThat(result.success()).isTrue();
            assertThat(result.reference()).startsWith("ch_");
        }
    }

    @Test
    void declineTokensFailWithReason() {
        assertThat(gateway.charge("k", "tok_declined", BigDecimal.TEN, "USD").declineReason()).isEqualTo("Card declined");
        assertThat(gateway.charge("k", "tok_insufficient_funds", BigDecimal.TEN, "USD").declineReason())
                .isEqualTo("Insufficient funds");
    }

    @Test
    void errorTokenSimulatesOutage() {
        assertThatThrownBy(() -> gateway.charge("k", "tok_gateway_error", BigDecimal.TEN, "USD"))
                .isInstanceOf(GatewayUnavailableException.class);
    }
}
