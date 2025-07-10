package kafka.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kafka.backend.model.ChatMessage;
import kafka.backend.repository.ChatMessageElasticsearchRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class KafkaConsumerService {

    @Autowired
    private ChatMessageElasticsearchRepository esRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "chat-topic", groupId = "chat-group")
    public void consume(String message) throws IOException {
        System.out.printf("Consumed message: %s%n", message);
        try {
            ChatMessage chatMessage = objectMapper.readValue(message, ChatMessage.class);
            esRepository.save(chatMessage);
            System.out.println("Saved message to Elasticsearch: " + chatMessage.getId());
        } catch (IOException e) {
            System.err.println("Error parsing Kafka message to ChatMessage: " + e.getMessage());
        }
    }
}
