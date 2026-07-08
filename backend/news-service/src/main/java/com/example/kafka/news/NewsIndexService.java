package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.NewsItem;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 피드/검색으로 가져온 기사를 요청 스레드 밖에서 Elasticsearch에 upsert한다(기사 URL 기준).
 * 색인 인덱스는 연관검색어 집계의 코퍼스가 된다. 모든 실패는 흡수 — 색인이 뉴스 서빙을 막지 않는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NewsIndexService {
    private final NewsSearchRepository repository;

    @Async
    public void indexQuietly(List<NewsItem> items, String category) {
        if (items == null || items.isEmpty()) {
            return;
        }
        try {
            Instant now = Instant.now();
            List<NewsDocument> documents = items.stream()
                    .filter(item -> item.url() != null && !item.url().isBlank())
                    .map(item -> NewsDocument.from(item, category, now))
                    .toList();
            if (!documents.isEmpty()) {
                repository.saveAll(documents);
            }
        } catch (RuntimeException exception) {
            log.debug("News indexing skipped (Elasticsearch unavailable): {}", exception.getMessage());
        }
    }
}
