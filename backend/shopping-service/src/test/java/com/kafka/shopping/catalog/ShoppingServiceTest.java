package com.kafka.shopping.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShoppingServiceTest {

    @Test
    void cleanTitleStripsBoldTagsAndEntities() {
        assertThat(ShoppingService.cleanTitle("애플 <b>맥북</b> 프로 &amp; 케이스"))
                .isEqualTo("애플 맥북 프로 & 케이스");
        assertThat(ShoppingService.cleanTitle("&lt;신상&gt; &quot;에어팟&quot;"))
                .isEqualTo("<신상> \"에어팟\"");
        assertThat(ShoppingService.cleanTitle(null)).isEmpty();
    }

    @Test
    void parsePriceHandlesStringsAndGarbage() {
        assertThat(ShoppingService.parsePrice("89000")).isEqualTo(89000L);
        assertThat(ShoppingService.parsePrice("  12345 ")).isEqualTo(12345L);
        assertThat(ShoppingService.parsePrice("")).isZero();
        assertThat(ShoppingService.parsePrice(null)).isZero();
        assertThat(ShoppingService.parsePrice("N/A")).isZero();
    }

    @Test
    void categoryLookupIsCaseInsensitiveAndCoversEight() {
        assertThat(ShoppingCategory.values()).hasSize(8);
        assertThat(ShoppingCategory.fromCode("ELECTRONICS")).contains(ShoppingCategory.ELECTRONICS);
        assertThat(ShoppingCategory.fromCode("mealkit")).contains(ShoppingCategory.MEALKIT);
        assertThat(ShoppingCategory.fromCode("nope")).isEmpty();
    }
}
