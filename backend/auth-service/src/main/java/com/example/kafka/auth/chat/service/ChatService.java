package com.example.kafka.auth.chat.service;

import com.example.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ContactResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.example.kafka.auth.chat.dto.ChatMessageEvent;
import com.example.kafka.auth.chat.model.ChatMessageDocument;
import com.example.kafka.auth.chat.model.ChatRoom;
import com.example.kafka.auth.chat.model.ChatRoomType;
import com.example.kafka.auth.chat.repository.ChatMessageRepository;
import com.example.kafka.auth.chat.repository.ChatRoomRepository;
import com.example.kafka.auth.chat.search.ChatMessageSearchDocument;
import com.example.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.example.kafka.auth.model.UserAccount;
import com.example.kafka.auth.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final UserAccountRepository userAccountRepository;
    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final String chatTopic;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMessageSearchRepository chatMessageSearchRepository,
            UserAccountRepository userAccountRepository,
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.chat.topic}") String chatTopic
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.userAccountRepository = userAccountRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.chatTopic = chatTopic;
    }

    @Transactional
    public ChatRoomResponse createRoom(CreateRoomRequest request, UserAccount user) {
        ChatRoom room = chatRoomRepository.save(new ChatRoom(request.name(), request.description(), user.getEmail()));
        return toRoomResponse(room);
    }

    @Transactional
    public ChatRoomResponse findOrCreateDirectRoom(CreateDirectRoomRequest request, UserAccount user) {
        UserAccount partner = userAccountRepository.findByEmail(request.partnerEmail())
                .orElseThrow(() -> new IllegalArgumentException("대화 상대를 찾을 수 없습니다."));
        if (partner.getEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("자기 자신과는 1:1 채팅방을 만들 수 없습니다.");
        }

        String directKey = directKey(user.getEmail(), partner.getEmail());
        ChatRoom room = chatRoomRepository.findByDirectKey(directKey)
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.direct(
                        directKey,
                        directRoomName(user, partner),
                        user.getEmail(),
                        new LinkedHashSet<>(List.of(user.getEmail(), partner.getEmail()))
                )));
        return toRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> rooms(String query, UserAccount user) {
        List<ChatRoom> rooms = query == null || query.isBlank()
                ? chatRoomRepository.findVisibleRooms(user.getEmail(), ChatRoomType.GROUP, PageRequest.of(0, 30))
                : chatRoomRepository.searchVisibleRooms(user.getEmail(), ChatRoomType.GROUP, query, PageRequest.of(0, 30));
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(String roomId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 50));
        Collections.reverse(messages);
        return messages.stream().map(this::toMessageResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(String query, UserAccount user) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return chatMessageSearchRepository
                    .findTop20ByContentContainingOrRoomNameContainingOrderByCreatedAtDesc(query, query)
                    .stream()
                    .filter(message -> canReadRoom(message.getRoomId(), user))
                    .map(this::toMessageResponse)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch search failed. Falling back to MongoDB search.", exception);
            return chatMessageRepository.findTop20ByContentContainingIgnoreCaseOrderByCreatedAtDesc(query)
                    .stream()
                    .filter(message -> canReadRoom(message.getRoomId(), user))
                    .map(this::toMessageResponse)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(String query, UserAccount currentUser) {
        List<UserAccount> users = query == null || query.isBlank()
                ? userAccountRepository.findTop30ByOrderByNameAsc()
                : userAccountRepository.findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(query, query);
        return users.stream()
                .filter(user -> !user.getEmail().equals(currentUser.getEmail()))
                .sorted(Comparator.comparing(UserAccount::getName))
                .map(user -> new ContactResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        user.getProvider().name()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatMessageEvent publishMessage(String roomId, SendMessageRequest request, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
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

    private ChatRoom ensureRoomAccess(String roomId, UserAccount user) {
        ChatRoom room = ensureRoom(roomId);
        if (!room.isVisibleTo(user.getEmail())) {
            throw new IllegalArgumentException("이 채팅방에 접근할 수 없습니다.");
        }
        return room;
    }

    private boolean canReadRoom(String roomId, UserAccount user) {
        return chatRoomRepository.findById(roomId)
                .map(room -> room.isVisibleTo(user.getEmail()))
                .orElse(false);
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        return new ChatRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getCreatedBy(),
                room.getType().name(),
                room.getCreatedAt()
        );
    }

    private String directKey(String firstEmail, String secondEmail) {
        return Set.of(firstEmail.toLowerCase(Locale.ROOT), secondEmail.toLowerCase(Locale.ROOT))
                .stream()
                .sorted()
                .reduce((first, second) -> first + "::" + second)
                .orElseThrow();
    }

    private String directRoomName(UserAccount user, UserAccount partner) {
        return user.getName() + ", " + partner.getName();
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
