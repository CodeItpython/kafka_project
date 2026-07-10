package com.kafka.shopping.search;

import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * A product indexed for search. Products flow in from Naver (feed/search/warming) and are
 * upserted by {@code productId}, so the index accumulates everything the app has ever fetched.
 * {@code title} is search_as_you_type to enable partial/prefix matching and autocomplete.
 */
@Document(indexName = "shopping-products", createIndex = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDocument {
    @Id
    private String productId;

    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String title;

    // title을 한국어 형태소(nori)로 분석한 사본. 연관검색어(significant_text) 집계 전용 —
    // search_as_you_type인 title은 형태소 분석이 안 돼 이 필드를 별도로 둔다.
    @Field(type = FieldType.Text, analyzer = "nori")
    private String titleNori;

    @Field(type = FieldType.Text)
    private String brand;

    @Field(type = FieldType.Keyword)
    private String mallName;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Long)
    private long price;

    @Field(type = FieldType.Keyword, index = false)
    private String link;

    @Field(type = FieldType.Keyword, index = false)
    private String image;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant indexedAt;

    public ProductDocument(
            String productId,
            String title,
            String brand,
            String mallName,
            String category,
            long price,
            String link,
            String image,
            Instant indexedAt
    ) {
        this.productId = productId;
        this.title = title;
        this.titleNori = title;
        this.brand = brand;
        this.mallName = mallName;
        this.category = category;
        this.price = price;
        this.link = link;
        this.image = image;
        this.indexedAt = indexedAt;
    }

    public static ProductDocument from(ProductResponse product, Instant indexedAt) {
        return new ProductDocument(
                product.productId(),
                product.title(),
                product.brand(),
                product.mallName(),
                product.category(),
                product.price(),
                product.link(),
                product.image(),
                indexedAt
        );
    }

    public ProductResponse toProduct() {
        return new ProductResponse(productId, title, link, image, price, 0L, mallName, brand, category);
    }
}
