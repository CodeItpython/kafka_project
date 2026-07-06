package com.kafka.shopping.search;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.catalog.ShoppingService;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
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
