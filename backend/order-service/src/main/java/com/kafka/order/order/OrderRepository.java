package com.kafka.order.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // Every read is user-scoped so a caller can only ever see their own orders.
    List<Order> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<Order> findByIdAndUserEmail(Long id, String userEmail);
}
