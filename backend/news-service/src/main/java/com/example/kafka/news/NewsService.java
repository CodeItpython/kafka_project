package com.example.kafka.news;

import com.example.kafka.news.NaverNewsApiClient.NaverNewsResponse;
import com.example.kafka.news.NewsDtos.NewsItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 네이버 뉴스 검색 API 기반 피드. start/display 페이지네이션을 지원하며 (category,start,display)별로
 * 짧은 TTL 캐시를 둔다. 호출 실패 시 (있다면) 만료 캐시라도 반환해 UX 공백을 줄인다.
 */
@Slf4j
@Service
public class NewsService {
    private static final int MAX_DISPLAY = 100;

    private final NaverNewsApiClient client;
    private final long ttlSeconds;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public NewsService(NaverNewsApiClient client, @Value("${app.news.cache-ttl-seconds:180}") long ttlSeconds) {
        this.client = client;
        this.ttlSeconds = ttlSeconds;
    }

    public List<NewsItem> feed(NewsCategory category, int start, int display, boolean forceRefresh) {
        int normalizedDisplay = clampDisplay(display);
        int normalizedStart = clampStart(start);
        String key = category.code() + ":" + normalizedStart + ":" + normalizedDisplay;

        CacheEntry entry = cache.get(key);
        Instant now = Instant.now();
        if (!forceRefresh && entry != null && now.isBefore(entry.expiresAt)) {
            return entry.items;
        }

        List<NewsItem> fresh = fetch(category, normalizedDisplay, normalizedStart);
        if (fresh.isEmpty() && entry != null) {
            log.info("Serving stale news cache for key={} (fresh fetch empty)", key);
            return entry.items;
        }
        cache.put(key, new CacheEntry(fresh, now.plus(Duration.ofSeconds(ttlSeconds))));
        return fresh;
    }

    private List<NewsItem> fetch(NewsCategory category, int display, int start) {
        NaverNewsResponse response = client.search(category.query(), display, start, "date");
        if (response == null || response.items() == null) {
            return List.of();
        }
        return response.items().stream().map(NewsService::toItem).toList();
    }

    private static NewsItem toItem(NaverNewsResponse.Item item) {
        String url = (item.link() != null && !item.link().isBlank()) ? item.link() : item.originalLink();
        return new NewsItem(
                url,               // id = 기사 URL(중복 제거 키)
                clean(item.title()),
                url,
                null,              // 검색 API는 언론사명을 제공하지 않음
                null,              // 검색 API는 썸네일을 제공하지 않음
                clean(item.description())
        );
    }

    /** 네이버는 매칭어를 &lt;b&gt;로 감싸고 일부 문자를 HTML 이스케이프한다. */
    static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("<[^>]+>", "")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .trim();
    }

    private int clampDisplay(int display) {
        if (display <= 0) {
            return 20;
        }
        return Math.min(display, MAX_DISPLAY);
    }

    private int clampStart(int start) {
        if (start < 1) {
            return 1;
        }
        return Math.min(start, 1000);
    }

    private record CacheEntry(List<NewsItem> items, Instant expiresAt) {
    }
}
