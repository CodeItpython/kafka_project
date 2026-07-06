package com.kafka.shopping.search;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface SearchLogRepository extends ElasticsearchRepository<SearchLogDocument, String> {
    /** Recent search logs within the popular-keyword window; ranking is computed in-memory. */
    List<SearchLogDocument> findByCreatedAtEpochGreaterThanEqual(long createdAtEpoch, Pageable pageable);
}
