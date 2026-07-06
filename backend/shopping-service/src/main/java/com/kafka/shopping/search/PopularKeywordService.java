package com.kafka.shopping.search;

import com.kafka.shopping.catalog.ShoppingDtos.PopularKeywordResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Ranks the most-searched keywords over a rolling window (default 24h). At this app's scale the
 * windowed logs are counted in-memory (cheap, exact) rather than via a terms aggregation; the
 * result is cached for a few seconds so the rolling UI and polling don't hammer Elasticsearch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopularKeywordService {
    private static final int MAX_SCAN = 5000;

    private final SearchLogRepository repository;
    private final SearchProperties properties;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public List<PopularKeywordResponse> top() {
        Cached cached = cache.get();
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.data();
        }
        List<PopularKeywordResponse> data = compute();
        long ttl = Math.max(1, properties.getPopularCacheSeconds());
        cache.set(new Cached(Instant.now().plusSeconds(ttl), data));
        return data;
    }

    private List<PopularKeywordResponse> compute() {
        try {
            long from = Instant.now().minus(properties.getPopularWindowHours(), ChronoUnit.HOURS).toEpochMilli();
            List<SearchLogDocument> logs =
                    repository.findByCreatedAtEpochGreaterThanEqual(from, PageRequest.of(0, MAX_SCAN));

            Map<String, long[]> counts = new HashMap<>();
            Map<String, String> displays = new HashMap<>();
            for (SearchLogDocument doc : logs) {
                String key = doc.getKeyword();
                if (key == null || key.isBlank()) {
                    continue;
                }
                counts.computeIfAbsent(key, ignored -> new long[1])[0]++;
                displays.putIfAbsent(key, doc.getKeywordDisplay() != null ? doc.getKeywordDisplay() : key);
            }

            List<Map.Entry<String, long[]>> sorted = new ArrayList<>(counts.entrySet());
            sorted.sort(Comparator.<Map.Entry<String, long[]>>comparingLong(entry -> entry.getValue()[0]).reversed()
                    .thenComparing(Map.Entry::getKey));

            int limit = Math.max(1, properties.getPopularSize());
            List<PopularKeywordResponse> result = new ArrayList<>();
            int rank = 1;
            for (Map.Entry<String, long[]> entry : sorted) {
                if (rank > limit) {
                    break;
                }
                result.add(new PopularKeywordResponse(rank++, displays.get(entry.getKey()), entry.getValue()[0]));
            }
            return List.copyOf(result);
        } catch (RuntimeException exception) {
            log.debug("Popular-keyword ranking unavailable (returning last known / empty): {}", exception.getMessage());
            Cached previous = cache.get();
            return previous != null ? previous.data() : List.of();
        }
    }

    private record Cached(Instant expiresAt, List<PopularKeywordResponse> data) {
    }
}
