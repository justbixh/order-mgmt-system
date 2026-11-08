package com.example.ordermgmt.payment;

import com.example.ordermgmt.contract.events.InventoryReservedEvent;
import com.example.ordermgmt.contract.events.PaymentFailedEvent;
import com.example.ordermgmt.contract.events.PaymentRefundedEvent;
import com.example.ordermgmt.contract.events.PaymentSucceededEvent;
import com.example.ordermgmt.messaging.OutboxService;
import com.example.ordermgmt.payment.domain.Payment;
import com.example.ordermgmt.payment.domain.PaymentRepository;
import com.example.ordermgmt.payment.domain.PaymentStatus;
import com.example.ordermgmt.payment.gateway.PaymentGateway;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic of the Payment Service.
 *
 * <h2>Idempotency, layered</h2>
 * <ol>
 *   <li>the shared {@code processed_events} check skips a re-delivered event;</li>
 *   <li>{@link #charge} looks for an existing payment for the order and skips if there is one;</li>
 *   <li>the unique {@code order_id} column makes a duplicate row impossible;</li>
 *   <li>the gateway call carries the order id as idempotency key, so the provider would not charge twice either.</li>
 * </ol>
 *
 * <p>Note on the gateway call inside a database transaction: it keeps the example simple, but a
 * slow provider holds a connection. At larger scale one would record a PENDING payment first and
 * call the gateway outside the transaction.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentGateway gateway;
    private final OutboxService outbox;

    public PaymentService(PaymentRepository payments, PaymentGateway gateway, OutboxService outbox) {
        this.payments = payments;
        this.gateway = gateway;
        this.outbox = outbox;
    }

    /**
     * Charges the customer for an order whose stock is reserved, then announces the result:
     * {@code PaymentSucceeded} or {@code PaymentFailed}.
     *
     * <p>If the gateway is unavailable this throws, so the event is retried and eventually dead-lettered.
     *
     * @param reserved the {@code InventoryReserved} payload
     */
    @Transactional
    public void charge(InventoryReservedEvent reserved) {
        Payment existing = payments.findByOrderId(reserved.orderId()).orElse(null);
        if (existing != null) {
            log.info("Payment for order {} already exists ({}), not charging again", reserved.orderId(), existing.getStatus());
            return;
        }

        var result = gateway.charge(reserved.orderId().toString(), reserved.paymentMethod(),
                reserved.totalAmount(), reserved.currency());

        if (result.success()) {
            Payment payment = payments.save(Payment.completed(reserved.orderId(), reserved.customerId(),
                    reserved.totalAmount(), reserved.currency(), reserved.paymentMethod(), result.reference()));
            outbox.publish(new PaymentSucceededEvent(reserved.orderId(), payment.getId(),
                    reserved.totalAmount(), reserved.currency()));
            log.info("Payment {} succeeded for order {} ({} {})", payment.getId(), reserved.orderId(),
                    reserved.totalAmount(), reserved.currency());
        } else {
            Payment payment = payments.save(Payment.failed(reserved.orderId(), reserved.customerId(),
                    reserved.totalAmount(), reserved.currency(), reserved.paymentMethod(), result.declineReason()));
            outbox.publish(new PaymentFailedEvent(reserved.orderId(), payment.getId(), result.declineReason()));
            log.warn("Payment {} failed for order {}: {}", payment.getId(), reserved.orderId(), result.declineReason());
        }
    }

    /**
     * <strong>Compensating transaction.</strong> The order was cancelled; make sure the customer is not charged.
     *
     * <ul>
     *   <li>payment {@code COMPLETED}: refund it and publish {@code PaymentRefunded};</li>
     *   <li>no payment yet: store a {@code CANCELLED} placeholder so a late charge request is skipped
     *       (events on different topics can arrive in any order);</li>
     *   <li>{@code FAILED}, {@code REFUNDED}, {@code CANCELLED}: nothing to do.</li>
     * </ul>
     *
     * @param orderId the cancelled order
     */
    @Transactional
    public void onOrderCancelled(UUID orderId) {
        Payment payment = payments.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            payments.save(Payment.cancelledBeforeCharge(orderId));
            log.info("Order {} cancelled before payment: recorded placeholder, it will not be charged", orderId);
            return;
        }
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.info("Order {} cancelled, payment is {}: nothing to refund", orderId, payment.getStatus());
            return;
        }
        gateway.refund(payment.getGatewayReference(), payment.getAmount());
        payment.markRefunded();
        outbox.publish(new PaymentRefundedEvent(orderId, payment.getId(), payment.getAmount(), payment.getCurrency()));
        log.info("Payment {} refunded for cancelled order {}", payment.getId(), orderId);
    }
}
