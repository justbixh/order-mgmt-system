package com.example.ordermgmt.order;

import com.example.ordermgmt.contract.events.OrderCancelledEvent;
import com.example.ordermgmt.contract.events.OrderCreatedEvent;
import com.example.ordermgmt.contract.events.OrderLine;
import com.example.ordermgmt.messaging.OutboxService;
import com.example.ordermgmt.order.domain.Order;
import com.example.ordermgmt.order.domain.OrderItem;
import com.example.ordermgmt.order.domain.OrderNotCancellableException;
import com.example.ordermgmt.order.domain.OrderNotFoundException;
import com.example.ordermgmt.order.domain.OrderRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use cases triggered by customers through the REST API. (Reactions to events from other services
 * live in {@link OrderSagaHandler}.)
 *
 * <p>Each command that changes state does two things in <strong>one</strong> transaction: it saves
 * the order and queues an event in the outbox. That is the Transactional Outbox pattern in action.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    /** Token understood by the mock payment gateway as "charge succeeds". */
    public static final String DEFAULT_PAYMENT_METHOD = "tok_success";

    private final OrderRepository orders;
    private final OutboxService outbox;

    public OrderService(OrderRepository orders, OutboxService outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Result of {@link #createOrder}: the order and whether it was newly created.
     *
     * @param order   the order
     * @param created {@code false} if the idempotency key matched an existing order
     */
    public record CreateResult(Order order, boolean created) {
    }

    /**
     * Places a new order (status {@code PENDING}) and starts the saga by queueing
     * {@code OrderCreated}.
     *
     * <p>If {@code idempotencyKey} was already used, the existing order is returned and nothing is
     * created. This protects against clients that retry after a timeout.
     *
     * @param customerId     who orders
     * @param customerEmail  notification address
     * @param items          the lines
     * @param currency       ISO 4217 code
     * @param paymentMethod  payment token, may be null
     * @param idempotencyKey optional client key, may be null
     * @return the order and whether it was created now
     */
    @Transactional
    public CreateResult createOrder(String customerId, String customerEmail, List<OrderItem> items,
                                    String currency, String paymentMethod, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotency-Key {} already used, returning order {}", idempotencyKey, existing.get().getId());
                return new CreateResult(existing.get(), false);
            }
        }
        String method = paymentMethod != null && !paymentMethod.isBlank() ? paymentMethod : DEFAULT_PAYMENT_METHOD;
        Order order = new Order(customerId, customerEmail, items, currency, method, idempotencyKey);
        try {
            // saveAndFlush so a duplicate idempotency key surfaces here, not at commit time.
            orders.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            // Two identical requests raced; the other one won. Fall back to its order.
            throw new IdempotencyConflictException(idempotencyKey, e);
        }

        outbox.publish(new OrderCreatedEvent(
                order.getId(), order.getCustomerId(), order.getCustomerEmail(),
                order.getItems().stream()
                        .map(i -> new OrderLine(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                        .toList(),
                order.getTotalAmount(), order.getCurrency(), order.getPaymentMethod()));

        log.info("Order {} created for customer {} total {} {} ({} item lines)",
                order.getId(), customerId, order.getTotalAmount(), currency, items.size());
        return new CreateResult(order, true);
    }

    /**
     * Cancels an order on behalf of the customer. Allowed until the order is confirmed.
     *
     * <p>Queues {@code OrderCancelled}, which makes Inventory release the stock and Payment refund
     * any charge: the same compensation path used when a saga step fails.
     *
     * @param orderId the order to cancel
     * @return the cancelled order
     * @throws OrderNotFoundException       if it does not exist
     * @throws OrderNotCancellableException if it is already confirmed or cancelled
     */
    @Transactional
    public Order cancelOrder(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus().isTerminal()) {
            throw new OrderNotCancellableException(orderId, order.getStatus());
        }
        order.cancel("Cancelled by customer");
        outbox.publish(new OrderCancelledEvent(
                order.getId(), order.getCustomerId(), order.getCustomerEmail(), order.getFailureReason()));
        log.info("Order {} cancelled by customer", orderId);
        return order;
    }

    /** @throws OrderNotFoundException if the order does not exist */
    @Transactional(readOnly = true)
    public Order getOrder(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /** Lists a customer's orders, newest page first as given by {@code pageable}. */
    @Transactional(readOnly = true)
    public Page<Order> listOrders(String customerId, Pageable pageable) {
        return customerId == null ? orders.findAll(pageable) : orders.findByCustomerId(customerId, pageable);
    }
}
