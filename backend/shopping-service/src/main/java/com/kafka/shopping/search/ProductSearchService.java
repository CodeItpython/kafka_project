package com.kafka.shopping.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.SignificantStringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.catalog.ShoppingService;
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
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

/**
 * Product search served from Elasticsearch (partial/prefix matching via search_as_you_type),
 * with a transparent fallback to Naver when the index is cold or ES is unavailable. Fallback
 * results are indexed by {@link ShoppingService}'s fetch side-effect, warming ES for next time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSearchService {
    private static final int MAX_DISPLAY = 100;
    private static final List<String> BOOL_PREFIX_FIELDS = List.of(
            "title^3",
            "title._2gram^2",
            "title._3gram^2",
            "title._index_prefix^4",
            "brand"
    );
    private static final List<String> EXACT_FIELDS = List.of("title^2", "brand");

    private final ElasticsearchOperations elasticsearchOperations;
    private final ShoppingService shoppingService;

    public List<ProductResponse> search(String query, String sort, int display, int start) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int size = clampDisplay(display);
        int page = Math.max(0, (clampStart(start) - 1) / size);
        try {
            List<ProductResponse> hits = executeEs(normalized, sort, size, page);
            if (!hits.isEmpty()) {
                return hits;
            }
        } catch (RuntimeException exception) {
            log.debug("Elasticsearch product search failed, falling back to Naver: {}", exception.getMessage());
        }
        return shoppingService.search(normalized, sort, display, start, false);
    }

    /**
     * 연관검색어: 시드 검색어와 매칭되는 상품 제목 집합에서, 전체 카탈로그 대비 통계적으로
     * 두드러지는 형태소(nori 분석)를 significant_text 집계로 뽑는다. ES가 없거나 색인이 비면
     * 빈 목록(프론트가 알아서 숨김). 검색어 자체 토큰·한 글자·숫자는 제외한다.
     */
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
                    .field("titleNori")
                    .size(limit + queryTokens.size() + 5)
                    .filterDuplicateText(true)
                    .minDocCount(2L)));
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.match(match -> match.field("titleNori").query(normalized)))
                    .withPageable(PageRequest.of(0, 1))
                    .withAggregation("related", related)
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
            AggregationsContainer<?> container = hits.getAggregations();
            if (!(container instanceof ElasticsearchAggregations aggregations)) {
                return List.of();
            }
            // 이름으로 조회(get("related"))하지 않고 리스트를 순회한다 — ELC가 응답 집계를
            // typed_key("sigsterms#related")로 키잉해 이름 조회가 null이 되는 것을 피한다.
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
            log.warn("Related-keyword aggregation failed: {}", exception.toString());
            return List.of();
        }
    }

    private List<ProductResponse> executeEs(String query, String sort, int size, int page) {
        var builder = NativeQuery.builder()
                .withQuery(root -> root.bool(bool -> bool
                        .should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(query)
                                .type(TextQueryType.BoolPrefix)
                                .fields(BOOL_PREFIX_FIELDS)))
                        .should(should -> should.multiMatch(multiMatch -> multiMatch
                                .query(query)
                                .fields(EXACT_FIELDS)))
                        .minimumShouldMatch("1")))
                .withPageable(PageRequest.of(page, size));

        String normalizedSort = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        if (normalizedSort.equals("asc")) {
            builder.withSort(sortBuilder -> sortBuilder.field(field -> field.field("price").order(SortOrder.Asc)));
        } else if (normalizedSort.equals("dsc")) {
            builder.withSort(sortBuilder -> sortBuilder.field(field -> field.field("price").order(SortOrder.Desc)));
        }

        return elasticsearchOperations.search(builder.build(), ProductDocument.class)
                .stream()
                .map(SearchHit::getContent)
                .map(ProductDocument::toProduct)
                .toList();
    }

    private int clampDisplay(int display) {
        if (display <= 0) {
            return 20;
        }
        return Math.min(display, MAX_DISPLAY);
    }

    private int clampStart(int start) {
        return start < 1 ? 1 : Math.min(start, 1000);
    }
}
