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

    /** A ranked popular search keyword (1 = most searched in the window). */
    public record PopularKeywordResponse(int rank, String keyword, long count) {
    }
}
