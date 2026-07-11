package com.kafka.order.order;

import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record OrderItemResponse(
            String productId,
            String title,
            String link,
            String image,
            long price,
            int quantity,
            String mallName,
            String brand
    ) {}

    public record OrderResponse(
            Long id,
            String orderNumber,
            String status,
            String paymentMethod,
            String paymentTxId,
            int totalCount,
            long totalAmount,
            Instant createdAt,
            List<OrderItemResponse> items
    ) {}

    public static OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getProductId(), item.getTitle(), item.getLink(), item.getImage(),
                        item.getPrice(), item.getQuantity(), item.getMallName(), item.getBrand()))
                .toList();
        return new OrderResponse(
                order.getId(), order.getOrderNumber(), order.getStatus(),
                order.getPaymentMethod(), order.getPaymentTxId(),
                order.getTotalCount(), order.getTotalAmount(), order.getCreatedAt(), items);
    }
}
