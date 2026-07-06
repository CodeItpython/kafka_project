package com.kafka.shopping.search;

import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Upserts products into Elasticsearch off the request thread. Called whenever the app fetches
 * from Naver (feed/search/warming) so the search index passively accumulates the live catalog.
 * All failures are swallowed — indexing must never break a product-serving request.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexService {
    private final ProductSearchRepository repository;

    @Async
    public void indexQuietly(List<ProductResponse> products) {
        if (products == null || products.isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            List<ProductDocument> documents = products.stream()
                    .filter(product -> product.productId() != null && !product.productId().isBlank())
                    .map(product -> ProductDocument.from(product, now))
                    .toList();
            if (!documents.isEmpty()) {
                repository.saveAll(documents);
            }
        } catch (RuntimeException exception) {
            log.debug("Product indexing skipped (Elasticsearch unavailable): {}", exception.getMessage());
        }
    }
}
