package com.kafka.auth.chat.repository;

import com.kafka.auth.chat.model.ChatMessageDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessageDocument, String> {
    List<ChatMessageDocument> findByRoomId(String roomId);

    List<ChatMessageDocument> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

    Optional<ChatMessageDocument> findFirstByRoomIdOrderByCreatedAtDesc(String roomId);

    List<ChatMessageDocument> findTop20ByContentContainingIgnoreCaseOrderByCreatedAtDesc(String content);
}
