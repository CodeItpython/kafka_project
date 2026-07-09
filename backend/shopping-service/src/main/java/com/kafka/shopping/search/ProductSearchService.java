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
import java.util.regex.Pattern;
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

    private static final List<String> SUGGEST_FIELDS = List.of(
            "title",
            "title._2gram",
            "title._3gram",
            "title._index_prefix"
    );
    // 자동완성 토큰화/필터용 정규식은 한 번만 컴파일한다(요청마다 재컴파일 방지).
    // 공백 + 문장부호(대괄호/괄호/따옴표/전각·스마트 따옴표/…·, .)로 분할.
    private static final Pattern SUGGEST_TOKEN_SPLIT = Pattern.compile("[\\s\\[\\]()\"'…·,.＂“”‘’]+");
    private static final Pattern MODEL_CODE = Pattern.compile("[A-Za-z0-9\\-]+");
    private static final Pattern HAS_DIGIT = Pattern.compile(".*[0-9].*");

    private final ElasticsearchOperations elasticsearchOperations;
    private final ShoppingService shoppingService;

    /**
     * 자동완성: 입력 중인 prefix로 상품 제목(search_as_you_type)을 bool_prefix 매칭해 짧은 구절 후보를
     * 돌려준다. ES 색인에 그 prefix가 아직 없으면(처음 치는 브랜드 등) Naver로 보강한다 —
     * ShoppingService.search가 Naver 결과를 색인까지 하므로 다음 입력부터는 ES로 즉시 커버된다.
     */
    public List<String> suggest(String prefix, int size) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(size, 10));
        // 다중어 입력("삼성 노트북")이면 마지막(완성 중인) 단어로 제목 토큰을 매칭한다 —
        // 전체 구절은 어떤 단일 토큰에도 포함되지 않아 첫 단어로 잘못 떨어지던 문제 방지.
        String matchLower = lastWord(normalized.toLowerCase(Locale.ROOT));
        List<String> result = new ArrayList<>();
        try {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.multiMatch(multiMatch -> multiMatch
                            .query(normalized)
                            .type(TextQueryType.BoolPrefix)
                            .fields(SUGGEST_FIELDS)))
                    .withPageable(PageRequest.of(0, Math.max(limit * 3, 20)))
                    .build();
            elasticsearchOperations.search(nativeQuery, ProductDocument.class)
                    .stream()
                    .map(hit -> hit.getContent().getTitle())
                    .filter(title -> title != null && !title.isBlank())
                    .forEach(title -> addSuggestion(result, shortenForSuggest(title, matchLower), limit));
        } catch (RuntimeException exception) {
            log.debug("Suggest (ES) unavailable: {}", exception.getMessage());
        }
        if (result.isEmpty()) {
            try {
                for (ProductResponse product : shoppingService.search(normalized, "sim", 20, 1, false)) {
                    if (product.title() != null) {
                        addSuggestion(result, shortenForSuggest(product.title(), matchLower), limit);
                    }
                    if (result.size() >= limit) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                log.debug("Suggest (Naver fallback) unavailable: {}", exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static void addSuggestion(List<String> result, String suggestion, int limit) {
        if (suggestion == null || suggestion.isBlank() || result.size() >= limit || result.contains(suggestion)) {
            return;
        }
        result.add(suggestion);
    }

    /** 입력의 마지막 공백 토큰(완성 중인 단어). 공백이 없으면 그대로 반환. */
    private static String lastWord(String text) {
        int lastSpace = text.lastIndexOf(' ');
        return (lastSpace >= 0 && lastSpace < text.length() - 1) ? text.substring(lastSpace + 1) : text;
    }

    /**
     * 상품 제목에서 자동완성 구절을 뽑는다: matchLower(완성 중인 단어)가 든 토큰 + 다음 "의미있는" 토큰
     * 하나를 붙여 구절로 만든다(단어 하나만 나오지 않게). 공백+문장부호로 토큰화하고, 모델코드/숫자성
     * 다음 토큰은 붙이지 않는다.
     */
    private static String shortenForSuggest(String title, String matchLower) {
        String[] tokens = SUGGEST_TOKEN_SPLIT.split(title);
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index].trim();
            if (token.isBlank() || !token.toLowerCase(Locale.ROOT).contains(matchLower)) {
                continue;
            }
            if (index + 1 < tokens.length) {
                String next = tokens[index + 1].trim();
                if (isMeaningfulNext(next) && (token + " " + next).length() <= 16) {
                    return token + " " + next;
                }
            }
            return token;
        }
        for (String token : tokens) {
            if (!token.isBlank()) {
                return token.trim();
            }
        }
        return "";
    }

    /** 구절에 붙일 다음 토큰이 의미있는지(순수 모델코드/숫자성 토큰은 제외). */
    private static boolean isMeaningfulNext(String token) {
        if (token.length() < 2) {
            return false;
        }
        return !(MODEL_CODE.matcher(token).matches() && HAS_DIGIT.matcher(token).matches());
    }

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
                    // 검색어 토큰 수는 상한을 둔다 — 비정상적으로 긴 질의가 버킷 수를 폭증시켜
                    // ES too_many_buckets로 실패하는 것을 방지.
                    .size(limit + Math.min(queryTokens.size(), 10) + 5)
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
