package com.example.ordermgmt.order.web;

import com.example.ordermgmt.order.OrderService;
import com.example.ordermgmt.order.domain.OrderItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for customers. Creating an order returns immediately with status {@code PENDING}: the
 * rest of the saga happens asynchronously, so clients poll {@code GET /api/orders/{id}} to follow it.
 */
@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** Places an order. Replaying the same {@code Idempotency-Key} returns the original order (HTTP 200). */
    @PostMapping
    @Operation(summary = "Place an order",
            description = "Returns 201 with status PENDING. Stock reservation and payment happen asynchronously.")
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @Parameter(description = "Optional key making retries safe") @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {
        List<OrderItem> items = request.items().stream()
                .map(i -> new OrderItem(i.productId(), i.quantity(), i.unitPrice()))
                .toList();
        var result = service.createOrder(request.customerId(), request.customerEmail(), items,
                request.currency(), request.paymentMethod(), idempotencyKey);
        OrderResponse body = OrderResponse.from(result.order());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.created(URI.create("/api/orders/" + body.id())).body(body);
    }

    /** Returns one order with its status history. */
    @GetMapping("/{id}")
    @Operation(summary = "Get an order and its status history")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.getOrder(id));
    }

    /** Lists orders, optionally for one customer, newest first. */
    @GetMapping
    @Operation(summary = "List orders (paged, newest first)")
    public PageResponse<OrderResponse> list(
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listOrders(customerId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResponse<>(result.map(OrderResponse::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /** Cancels an order that is not yet confirmed. */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an order", description = "Allowed until the order is confirmed. Triggers compensations.")
    public OrderResponse cancel(@PathVariable UUID id) {
        return OrderResponse.from(service.cancelOrder(id));
    }
}
