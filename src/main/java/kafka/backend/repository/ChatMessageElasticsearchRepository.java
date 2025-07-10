package kafka.backend.repository;

import kafka.backend.model.ChatMessage;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageElasticsearchRepository extends ElasticsearchRepository<ChatMessage, String> {
    List<ChatMessage> findByContentContaining(String content);
    List<ChatMessage> findBySender(String sender);
}