package com.example.kafka.news;

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
 * 카테고리별 뉴스를 캐시해 반환한다. 크롤링은 비용/차단 위험이 있어 TTL 캐시로 요청을 흡수하고,
 * 크롤링 실패 시에는 (있다면) 만료된 캐시라도 돌려줘 UX 공백을 줄인다.
 */
@Slf4j
@Service
public class NewsService {

    private final NaverNewsClient client;
    private final long ttlSeconds;
    private final Map<NewsCategory, CacheEntry> cache = new ConcurrentHashMap<>();

    public NewsService(NaverNewsClient client, @Value("${app.news.cache-ttl-seconds:180}") long ttlSeconds) {
        this.client = client;
        this.ttlSeconds = ttlSeconds;
    }

    public List<NewsItem> feed(NewsCategory category) {
        CacheEntry entry = cache.get(category);
        Instant now = Instant.now();
        if (entry != null && now.isBefore(entry.expiresAt)) {
            return entry.items;
        }
        List<NewsItem> fresh = client.fetch(category);
        if (fresh.isEmpty() && entry != null) {
            // 크롤 실패 → 만료된 캐시라도 유지해서 반환
            log.info("Serving stale cache for category={} (fresh fetch empty)", category.code());
            return entry.items;
        }
        cache.put(category, new CacheEntry(fresh, now.plus(Duration.ofSeconds(ttlSeconds))));
        return fresh;
    }

    private record CacheEntry(List<NewsItem> items, Instant expiresAt) {
    }
}
