package com.example.kafka.news;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.SignificantStringTermsBucket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

/**
 * 뉴스 연관검색어: 검색어와 매칭되는 기사 집합에서 전체 코퍼스 대비 두드러지는 형태소(nori 분석)를
 * significant_text 집계로 뽑는다. ES가 없거나 색인이 비면 빈 목록(프론트가 숨김).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsSearchService {
    private final ElasticsearchOperations elasticsearchOperations;

    public List<String> relatedKeywords(String query, int size) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(size, 20));
        Set<String> queryTokens = Arrays.stream(normalized.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toSet());
        try {
            Aggregation related = Aggregation.of(agg -> agg.significantText(text -> text
                    .field("contentNori")
                    .size(limit + queryTokens.size() + 5)
                    .filterDuplicateText(true)
                    .minDocCount(2L)));
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.match(match -> match.field("contentNori").query(normalized)))
                    .withPageable(PageRequest.of(0, 1))
                    .withAggregation("related", related)
                    .build();

            SearchHits<NewsDocument> hits = elasticsearchOperations.search(nativeQuery, NewsDocument.class);
            AggregationsContainer<?> container = hits.getAggregations();
            if (!(container instanceof ElasticsearchAggregations aggregations)) {
                return List.of();
            }
            // 이름 조회(get("related"))가 아니라 리스트 순회 — ELC가 집계를 typed_key로 키잉해 이름 조회가 null이 됨.
            List<String> result = new ArrayList<>();
            for (ElasticsearchAggregation aggregation : aggregations.aggregations()) {
                Aggregate aggregate = aggregation.aggregation().getAggregate();
                if (!aggregate.isSigsterms()) {
                    continue;
                }
                for (SignificantStringTermsBucket bucket : aggregate.sigsterms().buckets().array()) {
                    String key = bucket.key();
                    if (key == null) {
                        continue;
                    }
                    String lower = key.toLowerCase(Locale.ROOT);
                    if (queryTokens.contains(lower) || lower.length() < 2 || lower.chars().allMatch(Character::isDigit)) {
                        continue;
                    }
                    result.add(key);
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            log.warn("News related-keyword aggregation failed: {}", exception.toString());
            return List.of();
        }
    }
}
