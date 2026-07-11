package com.kafka.order.order;

import com.kafka.order.order.OrderDtos.OrderResponse;
import com.kafka.order.security.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    /** Places an order from the caller's current cart and runs the mock payment. */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(orderService.checkout(user.getEmail()));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(orderService.list(user.getEmail()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok(orderService.get(id, user.getEmail()));
    }
}
