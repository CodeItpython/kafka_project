package com.kafka.auth.chat.service;

import com.kafka.auth.chat.dto.ChatDtos.AttachmentRequest;
import com.kafka.auth.chat.dto.ChatDtos.AttachmentResponse;
import com.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.kafka.auth.chat.dto.ChatDtos.ContactResponse;
import com.kafka.auth.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.RoomPresenceResponse;
import com.kafka.auth.chat.dto.ChatDtos.SearchSuggestionResponse;
import com.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.chat.model.ChatMessageDocument;
import com.kafka.auth.chat.model.ChatRoom;
import com.kafka.auth.chat.model.ChatRoomType;
import com.kafka.auth.chat.repository.ChatMessageRepository;
import com.kafka.auth.chat.repository.ChatRoomRepository;
import com.kafka.auth.chat.search.ChatMessageSearchDocument;
import com.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.UserAccountRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final long MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final UserAccountRepository userAccountRepository;
    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatStateService chatStateService;
    private final String chatTopic;
    private final Path attachmentRoot;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMessageSearchRepository chatMessageSearchRepository,
            UserAccountRepository userAccountRepository,
            KafkaTemplate<String, ChatMessageEvent> kafkaTemplate,
            SimpMessagingTemplate messagingTemplate,
            ChatStateService chatStateService,
            @Value("${app.chat.topic}") String chatTopic,
            @Value("${app.chat.attachments.path:uploads/chat}") String attachmentPath
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.userAccountRepository = userAccountRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingTemplate = messagingTemplate;
        this.chatStateService = chatStateService;
        this.chatTopic = chatTopic;
        this.attachmentRoot = Paths.get(attachmentPath).toAbsolutePath().normalize();
    }

    @Transactional
    public ChatRoomResponse createRoom(CreateRoomRequest request, UserAccount user) {
        ChatRoom room = chatRoomRepository.save(new ChatRoom(request.name(), request.description(), user.getEmail()));
        chatStateService.evictRoomCaches(user.getEmail());
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
        chatStateService.evictRoomCaches(user.getEmail());
        chatStateService.evictRoomCaches(partner.getEmail());
        return toRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> rooms(String query, UserAccount user) {
        chatStateService.markOnline(user);
        return chatStateService.cachedRooms(user.getEmail(), query, () -> {
            List<ChatRoom> rooms = query == null || query.isBlank()
                    ? chatRoomRepository.findVisibleRooms(user.getEmail(), ChatRoomType.GROUP, PageRequest.of(0, 30))
                    : chatRoomRepository.searchVisibleRooms(user.getEmail(), ChatRoomType.GROUP, query, PageRequest.of(0, 30));
            return rooms.stream()
                    .filter(room -> room.isVisibleTo(user.getEmail()))
                    .map(this::toRoomResponse)
                    .toList();
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(String roomId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 50));
        Collections.reverse(messages);
        return messages.stream()
                .filter(message -> message.isVisibleTo(user.getEmail()))
                .map(this::toMessageResponse)
                .toList();
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
                    .filter(message -> canReadMessage(message.getId(), user))
                    .map(this::toMessageResponse)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch search failed. Falling back to MongoDB search.", exception);
            return chatMessageRepository.findTop20ByContentContainingIgnoreCaseOrderByCreatedAtDesc(query)
                    .stream()
                    .filter(message -> canReadMessage(message, user))
                    .map(this::toMessageResponse)
                    .toList();
        }
    }

    @Transactional(readOnly = true)
    public List<SearchSuggestionResponse> searchSuggestions(String query, String scope, UserAccount user) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        String normalizedQuery = query.trim();
        boolean includeRooms = scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope) || "rooms".equalsIgnoreCase(scope);
        boolean includeMessages = scope == null || scope.isBlank() || "all".equalsIgnoreCase(scope) || "messages".equalsIgnoreCase(scope);
        Map<String, SearchSuggestionResponse> suggestions = new LinkedHashMap<>();

        if (includeRooms) {
            chatRoomRepository.searchVisibleRooms(user.getEmail(), ChatRoomType.GROUP, normalizedQuery, PageRequest.of(0, 8))
                    .stream()
                    .filter(room -> room.isVisibleTo(user.getEmail()))
                    .forEach(room -> addSuggestion(suggestions, new SearchSuggestionResponse(
                            room.getName(),
                            "ROOM",
                            room.getId(),
                            room.getName()
                    )));
        }

        try {
            chatMessageSearchRepository
                    .findTop20ByContentContainingOrRoomNameContainingOrderByCreatedAtDesc(normalizedQuery, normalizedQuery)
                    .stream()
                    .filter(message -> canReadMessage(message.getId(), user))
                    .forEach(message -> {
                        if (includeRooms && containsIgnoreCase(message.getRoomName(), normalizedQuery)) {
                            addSuggestion(suggestions, new SearchSuggestionResponse(
                                    message.getRoomName(),
                                    "ROOM",
                                    message.getRoomId(),
                                    message.getRoomName()
                            ));
                        }
                        if (includeMessages) {
                            extractMessageSuggestions(message.getContent(), normalizedQuery)
                                    .forEach(text -> addSuggestion(suggestions, new SearchSuggestionResponse(
                                            text,
                                            "MESSAGE",
                                            message.getRoomId(),
                                            message.getRoomName()
                                    )));
                        }
                    });
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch suggestion lookup failed.", exception);
        }

        return suggestions.values().stream().limit(8).toList();
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(String query, UserAccount currentUser) {
        chatStateService.markOnline(currentUser);
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
                        user.getProvider().name(),
                        chatStateService.isOnline(user.getEmail())
                ))
                .toList();
    }

    public void heartbeat(UserAccount user) {
        chatStateService.markOnline(user);
    }

    public void setTyping(String roomId, boolean typing, UserAccount user) {
        ensureRoomAccess(roomId, user);
        chatStateService.markOnline(user);
        chatStateService.setTyping(roomId, user, typing);
    }

    @Transactional(readOnly = true)
    public RoomPresenceResponse roomPresence(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        chatStateService.markOnline(user);
        Set<String> participants = room.getType() == ChatRoomType.DIRECT
                ? room.getParticipantEmails()
                : userAccountRepository.findTop30ByOrderByNameAsc()
                .stream()
                .map(UserAccount::getEmail)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return chatStateService.roomPresence(roomId, participants, user.getEmail());
    }

    @Transactional(readOnly = true)
    public ChatMessageEvent publishMessage(String roomId, SendMessageRequest request, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        AttachmentRequest attachment = request.attachment();
        if ((request.content() == null || request.content().isBlank()) && attachment == null) {
            throw new IllegalArgumentException("메시지 내용이나 첨부 파일이 필요합니다.");
        }
        ChatMessageEvent event = new ChatMessageEvent(
                UUID.randomUUID().toString(),
                room.getId(),
                room.getName(),
                user.getEmail(),
                user.getName(),
                request.content() == null ? "" : request.content().trim(),
                attachment == null ? null : attachment.url(),
                attachment == null ? null : attachment.type(),
                attachment == null ? null : attachment.name(),
                attachment == null ? null : attachment.size(),
                Instant.now()
        );
        kafkaTemplate.send(chatTopic, roomId, event);
        return event;
    }

    @Transactional
    public void hideRoom(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        room.hideFor(user.getEmail());
        chatStateService.evictRoomCaches(user.getEmail());
    }

    @Transactional
    public ChatMessageResponse hideMessageForMe(String roomId, String messageId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        ChatMessageDocument message = ensureMessageInRoom(roomId, messageId);
        message.hideFor(user.getEmail());
        return toMessageResponse(chatMessageRepository.save(message));
    }

    @Transactional
    public void hideRoomMessagesForMe(String roomId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomId(roomId);
        messages.forEach(message -> message.hideFor(user.getEmail()));
        chatMessageRepository.saveAll(messages);
    }

    @Transactional
    public ChatMessageResponse deleteMessageForEveryone(String roomId, String messageId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        ChatMessageDocument message = ensureMessageInRoom(roomId, messageId);
        if (!message.getSenderEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("내가 보낸 메시지만 모두에게 삭제할 수 있습니다.");
        }
        message.deleteForEveryone();
        ChatMessageDocument saved = chatMessageRepository.save(message);
        try {
            chatMessageSearchRepository.deleteById(messageId);
        } catch (RuntimeException exception) {
            log.warn("Unable to delete chat message from Elasticsearch index.", exception);
        }
        ChatMessageResponse response = toMessageResponse(saved);
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
        return response;
    }

    public AttachmentResponse storeAttachment(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("첨부할 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("첨부 파일은 10MB 이하만 가능합니다.");
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지와 GIF 파일만 첨부할 수 있습니다.");
        }

        String extension = extension(file.getOriginalFilename(), contentType);
        String storedName = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(attachmentRoot);
            Path target = attachmentRoot.resolve(storedName).normalize();
            if (!target.startsWith(attachmentRoot)) {
                throw new IllegalArgumentException("잘못된 첨부 파일 경로입니다.");
            }
            file.transferTo(target);
            return new AttachmentResponse("/api/chat/attachments/" + storedName, contentType, cleanName(file.getOriginalFilename()), file.getSize());
        } catch (IOException exception) {
            throw new UncheckedIOException("첨부 파일 저장에 실패했습니다.", exception);
        }
    }

    public Resource loadAttachment(String fileName) {
        try {
            Path target = attachmentRoot.resolve(fileName).normalize();
            if (!target.startsWith(attachmentRoot) || !Files.exists(target)) {
                throw new IllegalArgumentException("첨부 파일을 찾을 수 없습니다.");
            }
            Resource resource = new UrlResource(target.toUri());
            if (!resource.isReadable()) {
                throw new IllegalArgumentException("첨부 파일을 읽을 수 없습니다.");
            }
            return resource;
        } catch (MalformedURLException exception) {
            throw new IllegalArgumentException("첨부 파일 경로가 올바르지 않습니다.", exception);
        }
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
                event.attachmentUrl(),
                event.attachmentType(),
                event.attachmentName(),
                event.attachmentSize(),
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

    private boolean canReadMessage(String messageId, UserAccount user) {
        return chatMessageRepository.findById(messageId)
                .map(message -> canReadMessage(message, user))
                .orElse(false);
    }

    private boolean canReadMessage(ChatMessageDocument message, UserAccount user) {
        return !message.isDeletedForEveryone()
                && message.isVisibleTo(user.getEmail())
                && canReadRoom(message.getRoomId(), user);
    }

    private void addSuggestion(Map<String, SearchSuggestionResponse> suggestions, SearchSuggestionResponse suggestion) {
        if (suggestion.text() == null || suggestion.text().isBlank()) {
            return;
        }
        String key = suggestion.type() + "::" + suggestion.text().toLowerCase(Locale.ROOT);
        suggestions.putIfAbsent(key, suggestion);
    }

    private List<String> extractMessageSuggestions(String content, String query) {
        if (content == null || content.isBlank() || !containsIgnoreCase(content, query)) {
            return List.of();
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<String> tokenMatches = Arrays.stream(content.split("[\\s,.;:!?()\\[\\]{}\"']+"))
                .map(String::trim)
                .filter(token -> token.length() >= normalizedQuery.length())
                .filter(token -> token.toLowerCase(Locale.ROOT).startsWith(normalizedQuery))
                .limit(3)
                .toList();
        if (!tokenMatches.isEmpty()) {
            return tokenMatches;
        }

        String lowerContent = content.toLowerCase(Locale.ROOT);
        int matchIndex = lowerContent.indexOf(normalizedQuery);
        int start = Math.max(0, matchIndex - 10);
        int end = Math.min(content.length(), matchIndex + query.length() + 24);
        String snippet = content.substring(start, end).trim();
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < content.length()) {
            snippet = snippet + "...";
        }
        return List.of(snippet);
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && query != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private ChatMessageDocument ensureMessageInRoom(String roomId, String messageId) {
        ChatMessageDocument message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));
        if (!message.getRoomId().equals(roomId)) {
            throw new IllegalArgumentException("채팅방의 메시지가 아닙니다.");
        }
        return message;
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

    private String extension(String fileName, String contentType) {
        String cleaned = cleanName(fileName);
        int dotIndex = cleaned.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < cleaned.length() - 1) {
            return cleaned.substring(dotIndex).toLowerCase(Locale.ROOT);
        }
        return switch (contentType) {
            case "image/gif" -> ".gif";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String cleanName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        return Paths.get(fileName).getFileName().toString().replaceAll("[^a-zA-Z0-9._가-힣-]", "_");
    }

    private ChatMessageResponse toMessageResponse(ChatMessageDocument message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getRoomName(),
                message.getSenderEmail(),
                message.getSenderName(),
                message.getContent(),
                message.getAttachmentUrl(),
                message.getAttachmentType(),
                message.getAttachmentName(),
                message.getAttachmentSize(),
                message.isDeletedForEveryone(),
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
                null,
                null,
                null,
                null,
                false,
                message.getCreatedAt()
        );
    }
}
