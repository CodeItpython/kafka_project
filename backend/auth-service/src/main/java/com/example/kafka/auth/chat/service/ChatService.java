package com.example.kafka.auth.chat.service;

import com.example.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.example.kafka.auth.chat.dto.ChatMessageEvent;
import com.example.kafka.auth.chat.model.ChatMessageDocument;
import com.example.kafka.auth.chat.model.ChatRoom;
import com.example.kafka.auth.chat.repository.ChatMessageRepository;
import com.example.kafka.auth.chat.repository.ChatRoomRepository;
import com.example.kafka.auth.chat.search.ChatMessageSearchDocument;
import com.example.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.example.kafka.auth.model.UserAccount;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final String chatTopic;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMessageSearchRepository chatMessageSearchRepository,
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.chat.topic}") String chatTopic
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.chatTopic = chatTopic;
    }

    @Transactional
    public ChatRoomResponse createRoom(CreateRoomRequest request, UserAccount user) {
        ChatRoom room = chatRoomRepository.save(new ChatRoom(request.name(), request.description(), user.getEmail()));
        return toRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> rooms(String query) {
        List<ChatRoom> rooms = query == null || query.isBlank()
                ? chatRoomRepository.findTop20ByOrderByCreatedAtDesc()
                : chatRoomRepository.findTop20ByNameContainingIgnoreCaseOrderByCreatedAtDesc(query);
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(String roomId) {
        ensureRoom(roomId);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 50));
        Collections.reverse(messages);
        return messages.stream().map(this::toMessageResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return chatMessageSearchRepository
                    .findTop20ByContentContainingOrRoomNameContainingOrderByCreatedAtDesc(query, query)
                    .stream()
                    .map(this::toMessageResponse)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch search failed. Falling back to MongoDB search.", exception);
            return chatMessageRepository.findTop20ByContentContainingIgnoreCaseOrderByCreatedAtDesc(query)
                    .stream()
                    .map(this::toMessageResponse)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public ChatMessageEvent publishMessage(String roomId, SendMessageRequest request, UserAccount user) {
        ChatRoom room = ensureRoom(roomId);
        ChatMessageEvent event = new ChatMessageEvent(
                UUID.randomUUID().toString(),
                room.getId(),
                room.getName(),
                user.getEmail(),
                user.getName(),
                request.content(),
                Instant.now()
        );
        kafkaTemplate.send(chatTopic, roomId, event);
        return event;
    }

    @KafkaListener(topics = "${app.chat.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void persistAndBroadcast(ChatMessageEvent event) {
        ChatMessageDocument savedMessage = chatMessageRepository.save(new ChatMessageDocument(
                event.messageId(),
                event.roomId(),
                event.roomName(),
                event.senderEmail(),
                event.senderName(),
                event.content(),
                event.createdAt()
        ));
        try {
            chatMessageSearchRepository.save(new ChatMessageSearchDocument(
                    savedMessage.getId(),
                    savedMessage.getRoomId(),
                    savedMessage.getRoomName(),
                    savedMessage.getSenderEmail(),
                    savedMessage.getSenderName(),
                    savedMessage.getContent(),
                    savedMessage.getCreatedAt()
            ));
        } catch (RuntimeException exception) {
            log.warn("Unable to index chat message into Elasticsearch. MongoDB history remains available.", exception);
        }
        messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId(), toMessageResponse(savedMessage));
    }

    private ChatRoom ensureRoom(String roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        return new ChatRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getCreatedBy(),
                room.getCreatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessageDocument message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getRoomName(),
                message.getSenderEmail(),
                message.getSenderName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }

    private ChatMessageResponse toMessageResponse(ChatMessageSearchDocument message) {
        return new ChatMessageResponse(
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
