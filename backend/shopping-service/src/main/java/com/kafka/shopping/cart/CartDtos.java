package com.kafka.shopping.cart;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class CartDtos {
    private CartDtos() {
    }

    public record AddCartItemRequest(
            @NotBlank String productId,
            @NotBlank String title,
            String link,
            String image,
            long price,
            String mallName,
            String brand,
            Integer quantity
    ) {
    }

    public record UpdateQuantityRequest(int quantity) {
    }

    public record CartItemResponse(
            Long id,
            String productId,
            String title,
            String link,
            String image,
            long price,
            String mallName,
            String brand,
            int quantity
    ) {
        static CartItemResponse from(CartItem item) {
            return new CartItemResponse(
                    item.getId(),
                    item.getProductId(),
                    item.getTitle(),
                    item.getLink(),
                    item.getImage(),
                    item.getPrice(),
                    item.getMallName(),
                    item.getBrand(),
                    item.getQuantity()
            );
        }
    }

    public record CartResponse(
            List<CartItemResponse> items,
            int totalCount,
            long totalPrice
    ) {
    }
}
