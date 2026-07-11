package com.kafka.order.order;

import com.kafka.order.client.CartResponse;
import com.kafka.order.client.ShoppingCartClient;
import com.kafka.order.order.OrderDtos.OrderResponse;
import com.kafka.order.payment.MockPaymentService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private static final DateTimeFormatter ORDER_NUMBER_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Seoul"));

    private final OrderRepository orderRepository;
    private final ShoppingCartClient shoppingCartClient;
    private final MockPaymentService mockPaymentService;

    // Per-user checkout lock: serializes a user's checkouts on this instance so a
    // double-submit can't turn one cart into two orders (the first clears the cart,
    // the second then sees it empty). Single-instance service — a multi-instance
    // deployment would need a shared lock (Redis) instead.
    private final ConcurrentHashMap<String, Object> checkoutLocks = new ConcurrentHashMap<>();

    /**
     * Reads the caller's authoritative cart from shopping-service, records the order
     * with a mock payment, then clears the cart. Not wrapped in a DB transaction so
     * the remote Feign reads don't pin a connection; the single save() is atomic and
     * the returned entity's items are the in-memory list built here (no lazy load).
     */
    public OrderResponse checkout(String userEmail) {
        Object lock = checkoutLocks.computeIfAbsent(userEmail, key -> new Object());
        synchronized (lock) {
            CartResponse cart = shoppingCartClient.getCart();
            if (cart == null || cart.items() == null || cart.items().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "장바구니가 비어 있어요.");
            }
            String base = ORDER_NUMBER_FMT.format(Instant.now());
            MockPaymentService.PaymentResult payment = mockPaymentService.charge(cart.totalPrice(), base);
            // Append the transaction-id tail so the user-facing order number is unique
            // even for two orders created within the same second.
            String tx = payment.transactionId();
            String orderNumber = base + "-" + tx.substring(Math.max(0, tx.length() - 4));

            Order order = new Order(orderNumber, userEmail, payment.status(), payment.method(),
                    tx, cart.totalCount(), cart.totalPrice());
            for (CartResponse.CartItem item : cart.items()) {
                order.addItem(new OrderItem(item.productId(), item.title(), item.link(), item.image(),
                        item.price(), item.quantity(), item.mallName(), item.brand()));
            }
            Order saved = orderRepository.save(order);

            // Best-effort: the order is already committed, so a cart-clear failure must
            // not fail checkout — just log it (the client also refreshes the cart).
            try {
                shoppingCartClient.clearCart();
            } catch (RuntimeException ex) {
                log.warn("order {} placed but cart clear failed: {}", orderNumber, ex.getMessage());
            }
            return OrderDtos.toResponse(saved);
        }
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String userEmail) {
        return orderRepository.findByUserEmailOrderByCreatedAtDesc(userEmail).stream()
                .map(OrderDtos::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id, String userEmail) {
        return orderRepository.findByIdAndUserEmail(id, userEmail)
                .map(OrderDtos::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없어요."));
    }
}
