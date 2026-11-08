package com.example.ordermgmt.payment.gateway;

import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fake payment provider whose behaviour is chosen by the payment token the customer sent when
 * placing the order. This makes every saga path testable from the command line.
 *
 * <table>
 *   <caption>Tokens</caption>
 *   <tr><th>token</th><th>behaviour</th></tr>
 *   <tr><td>{@code tok_success} (and anything unknown)</td><td>charge succeeds</td></tr>
 *   <tr><td>{@code tok_declined}</td><td>card declined</td></tr>
 *   <tr><td>{@code tok_insufficient_funds}</td><td>declined, insufficient funds</td></tr>
 *   <tr><td>{@code tok_gateway_error}</td><td>throws {@link GatewayUnavailableException} (outage)</td></tr>
 * </table>
 */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    @Override
    public GatewayResult charge(String idempotencyKey, String paymentMethod, BigDecimal amount, String currency) {
        log.info("[mock-gateway] charge {} {} with {} (key={})", amount, currency, paymentMethod, idempotencyKey);
        return switch (paymentMethod == null ? "" : paymentMethod) {
            case "tok_declined" -> new GatewayResult(false, null, "Card declined");
            case "tok_insufficient_funds" -> new GatewayResult(false, null, "Insufficient funds");
            case "tok_gateway_error" -> throw new GatewayUnavailableException("Mock gateway is down");
            default -> new GatewayResult(true, "ch_" + UUID.randomUUID().toString().substring(0, 12), null);
        };
    }

    @Override
    public void refund(String gatewayReference, BigDecimal amount) {
        log.info("[mock-gateway] refund {} for {}", amount, gatewayReference);
    }
}
