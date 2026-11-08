package com.example.ordermgmt.order.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link Order}. */
public interface OrderRepository extends JpaRepository<Order, UUID> {

    /** Finds the order created with the given client idempotency key, if any. */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    /** Pages through the orders of one customer. */
    Page<Order> findByCustomerId(String customerId, Pageable pageable);
}
