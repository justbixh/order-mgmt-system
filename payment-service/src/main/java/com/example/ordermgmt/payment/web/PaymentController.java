package com.example.ordermgmt.payment.web;

import com.example.ordermgmt.payment.domain.Payment;
import com.example.ordermgmt.payment.domain.PaymentRepository;
import com.example.ordermgmt.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only API to inspect payments. Charging happens only through events. */
@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    /** Payment as returned by the API. */
    public record PaymentView(UUID id, UUID orderId, PaymentStatus status, BigDecimal amount, String currency,
                              String failureReason, String gatewayReference, Instant createdAt, Instant updatedAt) {
        static PaymentView from(Payment p) {
            return new PaymentView(p.getId(), p.getOrderId(), p.getStatus(), p.getAmount(), p.getCurrency(),
                    p.getFailureReason(), p.getGatewayReference(), p.getCreatedAt(), p.getUpdatedAt());
        }
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get the payment of an order")
    public PaymentView byOrder(@PathVariable UUID orderId) {
        return payments.findByOrderId(orderId).map(PaymentView::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order " + orderId));
    }
}
