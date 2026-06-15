package com.kafka.auth.chat.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatMessageSearchIndexInitializer {
    private final ElasticsearchOperations elasticsearchOperations;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(ChatMessageSearchDocument.class);
            if (!indexOperations.exists()) {
                indexOperations.create();
            }
            indexOperations.putMapping(indexOperations.createMapping(ChatMessageSearchDocument.class));
            log.info("Chat message Elasticsearch index is ready.");
        } catch (RuntimeException exception) {
            log.warn("Chat message Elasticsearch index is not ready. Search will use MongoDB fallback until Elasticsearch is available.", exception);
        }
    }
}
