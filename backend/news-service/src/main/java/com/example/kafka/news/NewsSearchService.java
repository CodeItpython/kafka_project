package com.example.kafka.news;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.SignificantStringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.example.kafka.news.NewsDtos.NewsItem;
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
    private final NewsService newsService;

    /**
     * 자동완성: 입력 중인 prefix로 기사 제목(search_as_you_type)을 bool_prefix 매칭해 짧은 구절 후보를
     * 돌려준다. ES 색인에 그 prefix가 아직 없으면 Naver 검색으로 보강한다 — NewsService.search가 결과를
     * 색인까지 하므로 다음 입력부터는 ES로 즉시 커버된다(자기 부트스트랩).
     */
    public List<String> suggest(String prefix, int size) {
        String normalized = prefix == null ? "" : prefix.trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(size, 10));
        String prefixLower = normalized.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        try {
            NativeQuery nativeQuery = NativeQuery.builder()
                    .withQuery(root -> root.multiMatch(multiMatch -> multiMatch
                            .query(normalized)
                            .type(TextQueryType.BoolPrefix)
                            .fields(SUGGEST_FIELDS)))
                    .withPageable(PageRequest.of(0, Math.max(limit * 3, 20)))
                    .build();
            elasticsearchOperations.search(nativeQuery, NewsDocument.class)
                    .stream()
                    .map(hit -> hit.getContent().getTitle())
                    .filter(title -> title != null && !title.isBlank())
                    .forEach(title -> addSuggestion(result, shortenForSuggest(title, prefixLower), limit));
        } catch (RuntimeException exception) {
            log.debug("News suggest (ES) unavailable: {}", exception.getMessage());
        }
        if (result.isEmpty()) {
            try {
                for (NewsItem item : newsService.search(normalized, 1, 20)) {
                    if (item.title() != null) {
                        addSuggestion(result, shortenForSuggest(item.title(), prefixLower), limit);
                    }
                    if (result.size() >= limit) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                log.debug("News suggest (Naver fallback) unavailable: {}", exception.getMessage());
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

    /**
     * 긴 기사 제목(문장)에서 자동완성 구절을 뽑는다. 공백+문장부호(…·"'()[],. 등)로 토큰화해 prefix가
     * 든 토큰 + 다음 의미토큰 하나를 붙여 구절로 반환("변수"…코스피"에서도 "코스피"만 깔끔히, 단어 하나만
     * 나오지 않게). 모델코드/숫자성 다음 토큰은 붙이지 않는다.
     */
    private static String shortenForSuggest(String title, String prefixLower) {
        String[] tokens = title.split("[\\s\\[\\]()\"'…·,.＂“”‘’]+");
        for (int index = 0; index < tokens.length; index++) {
            String token = tokens[index].trim();
            if (token.isBlank() || !token.toLowerCase(Locale.ROOT).contains(prefixLower)) {
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
        return !(token.matches("[A-Za-z0-9\\-]+") && token.matches(".*[0-9].*"));
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
