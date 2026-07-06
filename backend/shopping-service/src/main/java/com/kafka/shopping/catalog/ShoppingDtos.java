package com.kafka.shopping.catalog;

public final class ShoppingDtos {
    private ShoppingDtos() {
    }

    public record CategoryResponse(String code, String label) {
    }

    /** A normalized product (HTML stripped from title, price parsed) ready for the UI. */
    public record ProductResponse(
            String productId,
            String title,
            String link,
            String image,
            long price,
            String mallName,
            String brand,
            String category
    ) {
    }
}
