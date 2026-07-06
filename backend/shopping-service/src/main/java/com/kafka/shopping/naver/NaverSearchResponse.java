package com.kafka.shopping.naver;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Raw shape of Naver's shopping search API (openapi.naver.com/v1/search/shop.json). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NaverSearchResponse(
        int total,
        int start,
        int display,
        List<Item> items
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(
            String title,
            String link,
            String image,
            String lprice,
            String hprice,
            String mallName,
            String productId,
            String productType,
            String brand,
            String maker,
            String category1,
            String category2,
            String category3,
            String category4
    ) {
    }
}
