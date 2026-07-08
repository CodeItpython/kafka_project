package com.example.kafka.news;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.SignificantStringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
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
    private static final List<String> SUGGEST_FIELDS = List.of(
            "titleSuggest",
            "titleSuggest._2gram",
            "titleSuggest._3gram",
            "titleSuggest._index_prefix"
    );

    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * 자동완성: 입력 중인 prefix로 기사 제목(search_as_you_type)을 bool_prefix 매칭해 짧은 키워드
     * 후보를 돌려준다. 색인이 비었거나 ES가 없으면 빈 목록(프론트가 드롭다운을 숨김).
     */
    public List<String> suggest(String prefix, int size) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(size, 10));
        try {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.multiMatch(multiMatch -> multiMatch
                            .query(normalized)
                            .type(TextQueryType.BoolPrefix)
                            .fields(SUGGEST_FIELDS)))
                    .withPageable(PageRequest.of(0, Math.max(limit * 3, 20)))
                    .build();
            String prefixLower = normalized.toLowerCase(Locale.ROOT);
            return elasticsearchOperations.search(nativeQuery, NewsDocument.class)
                    .stream()
                    .map(hit -> hit.getContent().getTitle())
                    .filter(title -> title != null && !title.isBlank())
                    .map(title -> shortenForSuggest(title, prefixLower))
                    .filter(suggestion -> suggestion != null && !suggestion.isBlank())
                    .distinct()
                    .limit(limit)
                    .toList();
        } catch (RuntimeException exception) {
            log.debug("News suggest unavailable: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 긴 기사 제목(문장)에서 자동완성용 짧은 키워드를 뽑는다. 공백뿐 아니라 문장부호(…·"'()[],.
     * 등)로도 토큰화해 prefix가 든 첫 토큰을 반환한다(짧으면 다음 토큰까지) — "변수"…코스피" 같은
     * 부호로 붙은 조각에서 "코스피"만 깔끔히 뽑기 위함.
     */
    private static String shortenForSuggest(String title, String prefixLower) {
        String[] tokens = title.split("[\\s\\[\\]()\"'…·,.＂“”‘’]+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index].trim();
            if (!token.isBlank() && token.toLowerCase(Locale.ROOT).contains(prefixLower)) {
                if (token.length() < 3 && index + 1 < tokens.length && !tokens[index + 1].isBlank()) {
                    token = (token + " " + tokens[index + 1].trim()).trim();
                }
                return token;
            }
        }
        for (String token : tokens) {
            if (!token.isBlank()) {
                return token.trim();
            }
        }
        return "";
    }

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
