package com.kafka.shopping.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** Upsert products by id; search itself is done via {@link ProductSearchService}. */
public interface ProductSearchRepository extends ElasticsearchRepository<ProductDocument, String> {
}
