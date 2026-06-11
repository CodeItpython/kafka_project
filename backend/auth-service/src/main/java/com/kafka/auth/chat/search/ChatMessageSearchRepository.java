package com.kafka.auth.chat.search;

import java.util.List;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ChatMessageSearchRepository extends ElasticsearchRepository<ChatMessageSearchDocument, String> {
    List<ChatMessageSearchDocument> findTop20ByContentContainingOrRoomNameContainingOrderByCreatedAtDesc(String content, String roomName);
}
