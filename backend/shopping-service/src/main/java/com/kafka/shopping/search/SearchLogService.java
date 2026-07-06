package com.kafka.shopping.search;

import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Records each user search into the {@code shopping-searches} index (off-thread, best-effort).
 * The normalized keyword drives popular-keyword grouping; the display form is kept for the UI
 * and Kibana. Never throws into the caller.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchLogService {
    private final SearchLogRepository repository;

    @Async
    public void logQuietly(String rawKeyword, String userEmail, int resultCount, String category) {
        String display = rawKeyword == null ? "" : rawKeyword.trim().replaceAll("\\s+", " ");
        if (display.isBlank()) {
            return;
        }
        String normalized = display.toLowerCase(Locale.ROOT);
        try {
            repository.save(new SearchLogDocument(normalized, display, userEmail, category, resultCount, Instant.now()));
        } catch (RuntimeException exception) {
            log.debug("Search logging skipped (Elasticsearch unavailable): {}", exception.getMessage());
        }
    }
}
