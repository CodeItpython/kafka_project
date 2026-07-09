package com.example.kafka.news;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * {@code news-articles} 인덱스를 애노테이션 매핑(contentNori=nori)으로 생성한다.
 * ES가 없으면 조용히 넘어가고(뉴스 피드/검색은 Naver로 계속 동작), ES 복구 시 다음 기동에 재시도된다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NewsSearchIndexInitializer {
    private final ElasticsearchOperations elasticsearchOperations;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(NewsDocument.class);
            // 이른 쓰기로 인덱스가 동적 자동생성되면 titleSuggest가 기본 text로 잡혀 자동완성이 깨진다.
            // titleSuggest가 search_as_you_type가 아니면 drop 후 재생성해 자가복구한다(shopping과 동일).
            if (indexOperations.exists()
                    && !hasFieldType(indexOperations.getMapping(), "titleSuggest", "search_as_you_type")) {
                log.info("Recreating news-articles index with the expected mapping.");
                indexOperations.delete();
            }
            if (!indexOperations.exists()) {
                indexOperations.create();
            }
            indexOperations.putMapping(indexOperations.createMapping(NewsDocument.class));
        } catch (RuntimeException exception) {
            log.warn("News search index not ready (retried once ES is reachable). Cause: {}", exception.getMessage());
        }
    }

    private boolean hasFieldType(Map<String, Object> mapping, String field, String expectedType) {
        Object properties = mapping.get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return false;
        }
        Object definition = propertiesMap.get(field);
        if (!(definition instanceof Map<?, ?> definitionMap)) {
            return false;
        }
        return expectedType.equals(definitionMap.get("type"));
    }
}
