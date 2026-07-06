package com.kafka.shopping.catalog;

import com.kafka.shopping.catalog.ShoppingDtos.CategoryResponse;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.naver.NaverSearchResponse;
import com.kafka.shopping.naver.NaverShoppingClient;
import com.kafka.shopping.search.ProductIndexService;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Serves category feeds and free-text search backed by Naver. Results are cached
 * in-memory for a short TTL so repeated tab switches don't burn the daily API quota.
 */
@Service
@Slf4j
public class ShoppingService {
    private static final Set<String> ALLOWED_SORTS = Set.of("sim", "date", "asc", "dsc");
    private static final int MAX_DISPLAY = 100;

    private final NaverShoppingClient naverClient;
    private final ProductIndexService productIndexService;
    private final Duration cacheTtl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ShoppingService(
            NaverShoppingClient naverClient,
            ProductIndexService productIndexService,
            @Value("${app.naver.cache-ttl-seconds:300}") long cacheTtlSeconds
    ) {
        this.naverClient = naverClient;
        this.productIndexService = productIndexService;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
    }

    public List<CategoryResponse> categories() {
        return Arrays.stream(ShoppingCategory.values())
                .map(category -> new CategoryResponse(category.code(), category.label()))
                .toList();
    }

    public List<ProductResponse> feed(String categoryCode, String sort, int display, int start, boolean refresh) {
        ShoppingCategory category = ShoppingCategory.fromCode(categoryCode)
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 카테고리입니다: " + categoryCode));
        String normalizedSort = normalizeSort(sort);
        int normalizedStart = clampStart(start);
        String key = "feed:" + category.code() + ":" + normalizedSort + ":" + clampDisplay(display) + ":" + normalizedStart;
        return cached(key, refresh, () -> fetch(category.query(), display, normalizedStart, normalizedSort));
    }

    public List<ProductResponse> search(String query, String sort, int display, int start, boolean refresh) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalizedSort = normalizeSort(sort);
        int normalizedStart = clampStart(start);
        String key = "search:" + query.trim().toLowerCase(Locale.ROOT) + ":" + normalizedSort + ":" + clampDisplay(display) + ":" + normalizedStart;
        return cached(key, refresh, () -> fetch(query.trim(), display, normalizedStart, normalizedSort));
    }

    private List<ProductResponse> fetch(String query, int display, int start, String sort) {
        NaverSearchResponse response = naverClient.search(query, clampDisplay(display), start, sort);
        if (response == null || response.items() == null) {
            return List.of();
        }
        List<ProductResponse> products = response.items().stream().map(ShoppingService::normalize).toList();
        // 조회된 상품을 Elasticsearch에 비동기 색인 → 검색 인덱스가 실카탈로그로 채워진다.
        productIndexService.indexQuietly(products);
        return products;
    }

    private static ProductResponse normalize(NaverSearchResponse.Item item) {
        return new ProductResponse(
                item.productId(),
                cleanTitle(item.title()),
                item.link(),
                item.image(),
                parsePrice(item.lprice()),
                item.mallName(),
                item.brand() == null || item.brand().isBlank() ? item.maker() : item.brand(),
                item.category1()
        );
    }

    /** Naver wraps matched terms in &lt;b&gt; tags and HTML-escapes some chars. */
    static String cleanTitle(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    static long parsePrice(String lprice) {
        if (lprice == null || lprice.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(lprice.trim());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String normalizeSort(String sort) {
        if (sort == null) {
            return "sim";
        }
        String lower = sort.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_SORTS.contains(lower) ? lower : "sim";
    }

    private int clampDisplay(int display) {
        if (display <= 0) {
            return 20;
        }
        return Math.min(display, MAX_DISPLAY);
    }

    /** Naver의 start는 1~1000 범위(+ display를 더해도 1000을 넘지 않도록 캡). */
    private int clampStart(int start) {
        if (start < 1) {
            return 1;
        }
        return Math.min(start, 1000);
    }

    private List<ProductResponse> cached(String key, boolean refresh, java.util.function.Supplier<List<ProductResponse>> loader) {
        if (!refresh) {
            CacheEntry entry = cache.get(key);
            if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
                return entry.data();
            }
        }
        List<ProductResponse> data = loader.get();
        cache.put(key, new CacheEntry(Instant.now().plus(cacheTtl), data));
        return data;
    }

    private record CacheEntry(Instant expiresAt, List<ProductResponse> data) {
    }
}
