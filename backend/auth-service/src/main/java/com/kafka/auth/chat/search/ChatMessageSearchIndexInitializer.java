package com.kafka.auth.chat.search;

import com.kafka.auth.chat.model.ChatMessageDocument;
import com.kafka.auth.chat.repository.ChatMessageRepository;
import java.util.List;
import java.util.Map;
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
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(ChatMessageSearchDocument.class);
            if (indexOperations.exists() && !usesSearchAsYouType(indexOperations.getMapping())) {
                log.info("Recreating chat message Elasticsearch index for search_as_you_type mapping.");
                indexOperations.delete();
            }
            if (!indexOperations.exists()) {
                indexOperations.create();
            }
            indexOperations.putMapping(indexOperations.createMapping(ChatMessageSearchDocument.class));
            reindexExistingMessages();
            log.info("Chat message Elasticsearch index is ready.");
        } catch (RuntimeException exception) {
            log.warn("Chat message Elasticsearch index is not ready. Search will use MongoDB fallback until Elasticsearch is available.", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean usesSearchAsYouType(Map<String, Object> mapping) {
        Object properties = mapping.get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return false;
        }
        return List.of("content", "roomName", "senderName").stream()
                .allMatch(field -> {
                    Object definition = propertiesMap.get(field);
                    if (!(definition instanceof Map<?, ?> definitionMap)) {
                        return false;
                    }
                    return "search_as_you_type".equals(definitionMap.get("type"));
                });
    }

    private void reindexExistingMessages() {
        List<ChatMessageSearchDocument> documents = chatMessageRepository.findAll()
                .stream()
                .filter(message -> !message.isDeletedForEveryone())
                .map(this::toSearchDocument)
                .toList();
        if (documents.isEmpty()) {
            return;
        }
        chatMessageSearchRepository.saveAll(documents);
        log.info("Reindexed {} chat messages into Elasticsearch.", documents.size());
    }

    private ChatMessageSearchDocument toSearchDocument(ChatMessageDocument message) {
        return new ChatMessageSearchDocument(
                message.getId(),
                message.getRoomId(),
                message.getRoomName(),
                message.getSenderEmail(),
                message.getSenderName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
