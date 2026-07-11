package com.kafka.order.client;

import java.util.List;

/** Mirror of shopping-service's CartResponse (the only fields order-service needs). */
public record CartResponse(List<CartItem> items, int totalCount, long totalPrice) {
    public record CartItem(
            Long id,
            String productId,
            String title,
            String link,
            String image,
            long price,
            String mallName,
            String brand,
            int quantity
    ) {}
}
