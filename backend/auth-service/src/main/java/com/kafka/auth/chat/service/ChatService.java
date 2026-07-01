package com.kafka.auth.chat.service;

import com.kafka.auth.chat.dto.ChatDtos.AttachmentRequest;
import com.kafka.auth.chat.dto.ChatDtos.AttachmentResponse;
import com.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.kafka.auth.chat.dto.ChatDtos.ContactResponse;
import com.kafka.auth.chat.dto.ChatDtos.ConversationSummaryResponse;
import com.kafka.auth.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.EditMessageRequest;
import com.kafka.auth.chat.dto.ChatDtos.InviteRoomParticipantsRequest;
import com.kafka.auth.chat.dto.ChatDtos.MessageReactionRequest;
import com.kafka.auth.chat.dto.ChatDtos.MessageReactionResponse;
import com.kafka.auth.chat.dto.ChatDtos.RoomParticipantResponse;
import com.kafka.auth.chat.dto.ChatDtos.RoomPresenceResponse;
import com.kafka.auth.chat.dto.ChatDtos.RoomPreferenceRequest;
import com.kafka.auth.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.auth.chat.dto.ChatDtos.SearchSuggestionResponse;
import com.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.chat.model.ChatMessageDocument;
import com.kafka.auth.chat.model.ChatRoom;
import com.kafka.auth.chat.model.ChatRoomType;
import com.kafka.auth.chat.model.ChatRoomUserPreference;
import com.kafka.auth.chat.repository.ChatMessageRepository;
import com.kafka.auth.chat.repository.ChatRoomRepository;
import com.kafka.auth.chat.repository.ChatRoomUserPreferenceRepository;
import com.kafka.auth.chat.search.ChatMessageSearchDocument;
import com.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.kafka.auth.chat.search.ChatMessageSearchService;
import com.kafka.auth.chat.service.ChatStateService.UserProfileSnapshot;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.notification.NotificationService;
import com.kafka.auth.outbox.ChatMessageOutboxService;
import com.kafka.auth.repository.UserAccountRepository;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.kafka.auth.storage.ObjectStorageService;
import com.kafka.auth.storage.StoredObject;
import io.micrometer.core.instrument.Timer;

@Service
@Slf4j
public class ChatService {
    private static final long MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomUserPreferenceRepository chatRoomUserPreferenceRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageSearchRepository chatMessageSearchRepository;
    private final ChatMessageSearchService chatMessageSearchService;
    private final UserAccountRepository userAccountRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatStateService chatStateService;
    private final ChatMetricsService chatMetricsService;
    private final ChatReadReceiptService chatReadReceiptService;
    private final ChatSummaryService chatSummaryService;
    private final ObjectStorageService objectStorageService;
    private final ChatMessageOutboxService chatMessageOutboxService;
    private final NotificationService notificationService;
    private final String chatTopic;

    public ChatService(
            ChatRoomRepository chatRoomRepository,
            ChatRoomUserPreferenceRepository chatRoomUserPreferenceRepository,
            ChatMessageRepository chatMessageRepository,
            ChatMessageSearchRepository chatMessageSearchRepository,
            ChatMessageSearchService chatMessageSearchService,
            UserAccountRepository userAccountRepository,
            SimpMessagingTemplate messagingTemplate,
            ChatStateService chatStateService,
            ChatMetricsService chatMetricsService,
            ChatReadReceiptService chatReadReceiptService,
            ChatSummaryService chatSummaryService,
            ObjectStorageService objectStorageService,
            ChatMessageOutboxService chatMessageOutboxService,
            NotificationService notificationService,
            @Value("${app.chat.topic}") String chatTopic
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomUserPreferenceRepository = chatRoomUserPreferenceRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageSearchRepository = chatMessageSearchRepository;
        this.chatMessageSearchService = chatMessageSearchService;
        this.userAccountRepository = userAccountRepository;
        this.messagingTemplate = messagingTemplate;
        this.chatStateService = chatStateService;
        this.chatMetricsService = chatMetricsService;
        this.chatReadReceiptService = chatReadReceiptService;
        this.chatSummaryService = chatSummaryService;
        this.objectStorageService = objectStorageService;
        this.chatMessageOutboxService = chatMessageOutboxService;
        this.notificationService = notificationService;
        this.chatTopic = chatTopic;
    }

    @Transactional
    public ChatRoomResponse createRoom(CreateRoomRequest request, UserAccount user) {
        ChatRoom room = chatRoomRepository.save(new ChatRoom(request.name(), request.description(), user.getEmail()));
        chatStateService.evictRoomCaches(user.getEmail());
        return toRoomResponse(room, user.getEmail());
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
        return toRoomResponse(room, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<ChatRoomResponse> rooms(String query, UserAccount user) {
        chatStateService.markOnline(user);
        return chatStateService.cachedRooms(user.getEmail(), query, () -> {
            List<ChatRoom> rooms = query == null || query.isBlank()
                    ? chatRoomRepository.findVisibleRooms(user.getEmail(), ChatRoomType.GROUP, PageRequest.of(0, 30))
                    : chatRoomRepository.searchVisibleRooms(user.getEmail(), ChatRoomType.GROUP, query, PageRequest.of(0, 30));
            Map<String, ChatRoomUserPreference> preferences = roomPreferencesMap(rooms, user.getEmail());
            return rooms.stream()
                    .filter(room -> room.isVisibleTo(user.getEmail()))
                    .map(room -> toRoomResponse(room, user.getEmail(), preferences.get(room.getId())))
                    .sorted(roomResponseComparator())
                    .toList();
        });
    }

    @Transactional
    public List<ChatMessageResponse> messages(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 50));
        Collections.reverse(messages);
        RoomReadSummaryResponse readSummary = chatReadReceiptService.markRead(room, user, Instant.now());
        publishReadSummary(roomId, readSummary);
        Map<String, Instant> lastReadByEmail = chatReadReceiptService.lastReadByEmail(roomId);
        return messages.stream()
                .filter(message -> message.isVisibleTo(user.getEmail()))
                .map(message -> toMessageResponse(message, lastReadByEmail, user.getEmail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoomReadSummaryResponse readReceipts(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        return chatReadReceiptService.summary(room, user);
    }

    @Transactional
    public RoomReadSummaryResponse markRead(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        RoomReadSummaryResponse readSummary = chatReadReceiptService.markRead(room, user, Instant.now());
        publishReadSummary(roomId, readSummary);
        return readSummary;
    }

    @Transactional(readOnly = true)
    public ConversationSummaryResponse summarizeRoom(String roomId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        List<ChatMessageDocument> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, PageRequest.of(0, 80));
        Collections.reverse(messages);
        List<ChatMessageDocument> visibleMessages = messages.stream()
                .filter(message -> message.isVisibleTo(user.getEmail()))
                .toList();
        return chatSummaryService.summarize(visibleMessages);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> searchMessages(String query, UserAccount user) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return chatMetricsService.recordSearch("elasticsearch", () -> chatMessageSearchService
                            .search(query, 20)
                            .stream()
                            .filter(message -> canReadMessage(message.getId(), user))
                            .map(this::toMessageResponse)
                            .toList()
            );
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch search failed. Falling back to MongoDB search.", exception);
            return chatMetricsService.recordSearch("mongodb_fallback", () -> chatMessageRepository
                            .findTop20ByContentContainingIgnoreCaseOrderByCreatedAtDesc(query)
                            .stream()
                            .filter(message -> canReadMessage(message, user))
                            .map(message -> toMessageResponse(message, Map.of(), user.getEmail()))
                            .toList()
            );
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
            chatMetricsService.recordSearch("elasticsearch_suggestion", () -> {
                chatMessageSearchService
                        .suggestions(normalizedQuery, 20)
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
                return suggestions;
            });
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch suggestion lookup failed.", exception);
        }

        return suggestions.values().stream().limit(8).toList();
    }

    @Transactional(readOnly = true)
    public List<ContactResponse> contacts(String query, UserAccount currentUser) {
        chatStateService.markOnline(currentUser);
        List<UserProfileSnapshot> profiles = chatStateService.cachedUserProfiles(
                query,
                () -> query == null || query.isBlank()
                        ? userAccountRepository.findTop30ByOrderByNameAsc()
                        : userAccountRepository.findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(query, query)
        );
        return profiles.stream()
                .filter(profile -> !profile.email().equals(currentUser.getEmail()))
                .sorted(Comparator.comparing(UserProfileSnapshot::name))
                .map(profile -> new ContactResponse(
                        profile.id(),
                        profile.email(),
                        profile.name(),
                        profile.provider(),
                        profile.statusMessage(),
                        profile.profileImageUrl(),
                        chatStateService.isOnline(profile.email())
                ))
                .toList();
    }

    public void heartbeat(UserAccount user) {
        chatStateService.markOnline(user);
    }

    @Transactional(readOnly = true)
    public void setTyping(String roomId, boolean typing, UserAccount user) {
        ensureRoomAccess(roomId, user);
        chatStateService.markOnline(user);
        chatStateService.setTyping(roomId, user, typing);
    }

    @Transactional(readOnly = true)
    public RoomPresenceResponse roomPresence(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        chatStateService.markOnline(user);
        Set<String> participants = participantEmailsForRoom(room);
        return chatStateService.roomPresence(roomId, participants, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<RoomParticipantResponse> roomParticipants(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        return participantResponses(room);
    }

    @Transactional
    public List<RoomParticipantResponse> inviteRoomParticipants(String roomId, InviteRoomParticipantsRequest request, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        if (room.getType() != ChatRoomType.GROUP) {
            throw new IllegalArgumentException("그룹 채팅방에만 친구를 초대할 수 있습니다.");
        }
        if (!room.isCreatedBy(user.getEmail()) && !room.isParticipant(user.getEmail())) {
            throw new IllegalArgumentException("채팅방 참여자만 친구를 초대할 수 있습니다.");
        }
        List<String> emails = request.emails()
                .stream()
                .map(this::normalizeEmail)
                .filter(email -> !email.isBlank())
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            throw new IllegalArgumentException("초대할 사용자를 선택해주세요.");
        }
        List<UserAccount> invitees = userAccountRepository.findByEmailIn(emails);
        Set<String> foundEmails = invitees.stream()
                .map(UserAccount::getEmail)
                .map(this::normalizeEmail)
                .collect(java.util.stream.Collectors.toSet());
        List<String> missingEmails = emails.stream()
                .filter(email -> !foundEmails.contains(email))
                .toList();
        if (!missingEmails.isEmpty()) {
            throw new IllegalArgumentException("초대할 사용자를 찾을 수 없습니다: " + String.join(", ", missingEmails));
        }
        if (room.getParticipantEmails().isEmpty()) {
            room.addParticipant(room.getCreatedBy());
        }
        invitees.forEach(invitee -> room.addParticipant(invitee.getEmail()));
        chatRoomRepository.save(room);
        chatStateService.evictRoomCaches(user.getEmail());
        invitees.forEach(invitee -> chatStateService.evictRoomCaches(invitee.getEmail()));
        return participantResponses(room);
    }

    @Transactional
    public void leaveRoom(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        if (room.getType() != ChatRoomType.GROUP) {
            throw new IllegalArgumentException("1:1 채팅방에서는 나가기를 사용할 수 없습니다.");
        }
        if (room.getParticipantEmails().isEmpty()) {
            room.addParticipant(room.getCreatedBy());
        }
        if (!room.isParticipant(user.getEmail())) {
            room.hideFor(user.getEmail());
        } else {
            room.removeParticipant(user.getEmail());
        }
        chatRoomRepository.save(room);
        chatStateService.evictRoomCaches(user.getEmail());
    }

    @Transactional
    public ChatMessageEvent publishMessage(String roomId, SendMessageRequest request, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        AttachmentRequest attachment = request.attachment();
        if ((request.content() == null || request.content().isBlank()) && attachment == null) {
            throw new IllegalArgumentException("메시지 내용이나 첨부 파일이 필요합니다.");
        }
        ChatMessageDocument replySource = resolveReplySource(roomId, request.replyToMessageId(), user);
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
                replySource == null ? null : replySource.getId(),
                replySource == null ? null : replySource.getSenderName(),
                replySource == null ? null : replyPreview(replySource),
                Instant.now()
        );
        chatMessageOutboxService.append(event, chatTopic);
        return event;
    }

    @Transactional
    public void hideRoom(String roomId, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        room.hideFor(user.getEmail());
        chatStateService.evictRoomCaches(user.getEmail());
    }

    @Transactional
    public ChatRoomResponse updateRoomPreference(String roomId, RoomPreferenceRequest request, UserAccount user) {
        ChatRoom room = ensureRoomAccess(roomId, user);
        ChatRoomUserPreference preference = chatRoomUserPreferenceRepository
                .findByRoomIdAndUserEmail(roomId, user.getEmail())
                .orElseGet(() -> new ChatRoomUserPreference(roomId, user.getEmail()));
        preference.update(request.pinned(), request.muted());
        ChatRoomUserPreference saved = chatRoomUserPreferenceRepository.save(preference);
        chatStateService.evictRoomCaches(user.getEmail());
        return toRoomResponse(room, user.getEmail(), saved);
    }

    @Transactional
    public ChatMessageResponse hideMessageForMe(String roomId, String messageId, UserAccount user) {
        ensureRoomAccess(roomId, user);
        ChatMessageDocument message = ensureMessageInRoom(roomId, messageId);
        message.hideFor(user.getEmail());
        return toMessageResponse(chatMessageRepository.save(message), chatReadReceiptService.lastReadByEmail(roomId), user.getEmail());
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
        ChatMessageResponse response = toMessageResponse(saved, chatReadReceiptService.lastReadByEmail(roomId), user.getEmail());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
        return response;
    }

    @Transactional
    public ChatMessageResponse editMessage(String roomId, String messageId, EditMessageRequest request, UserAccount user) {
        ensureRoomAccess(roomId, user);
        ChatMessageDocument message = ensureMessageInRoom(roomId, messageId);
        if (!message.getSenderEmail().equalsIgnoreCase(user.getEmail())) {
            throw new IllegalArgumentException("내가 보낸 메시지만 수정할 수 있습니다.");
        }
        if (message.isDeletedForEveryone()) {
            throw new IllegalArgumentException("삭제된 메시지는 수정할 수 없습니다.");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("수정할 메시지 내용이 필요합니다.");
        }
        message.editContent(request.content().trim());
        ChatMessageDocument saved = chatMessageRepository.save(message);
        Timer.Sample indexSample = chatMetricsService.startTimer();
        try {
            chatMessageSearchRepository.save(new ChatMessageSearchDocument(
                    saved.getId(),
                    saved.getRoomId(),
                    saved.getRoomName(),
                    saved.getSenderEmail(),
                    saved.getSenderName(),
                    saved.getContent(),
                    saved.getCreatedAt()
            ));
            chatMetricsService.recordElasticsearchIndexSuccess(indexSample);
        } catch (RuntimeException exception) {
            chatMetricsService.recordElasticsearchIndexFailure(indexSample);
            log.warn("Unable to update chat message Elasticsearch index after edit.", exception);
        }
        Map<String, Instant> lastReadByEmail = chatReadReceiptService.lastReadByEmail(roomId);
        ChatMessageResponse response = toMessageResponse(saved, lastReadByEmail, user.getEmail());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, toMessageResponse(saved, lastReadByEmail, null));
        return response;
    }

    @Transactional
    public ChatMessageResponse toggleReaction(String roomId, String messageId, MessageReactionRequest request, UserAccount user) {
        ensureRoomAccess(roomId, user);
        ChatMessageDocument message = ensureMessageInRoom(roomId, messageId);
        if (message.isDeletedForEveryone()) {
            throw new IllegalArgumentException("삭제된 메시지에는 반응할 수 없습니다.");
        }
        String emoji = normalizeReaction(request.emoji());
        message.toggleReaction(emoji, user.getEmail());
        ChatMessageDocument saved = chatMessageRepository.save(message);
        Map<String, Instant> lastReadByEmail = chatReadReceiptService.lastReadByEmail(roomId);
        ChatMessageResponse response = toMessageResponse(saved, lastReadByEmail, user.getEmail());
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId, toMessageResponse(saved, lastReadByEmail, null));
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
        objectStorageService.store("chat/" + storedName, file, contentType);
        return new AttachmentResponse("/api/chat/attachments/" + storedName, contentType, cleanName(file.getOriginalFilename()), file.getSize());
    }

    public StoredObject loadAttachment(String fileName) {
        return objectStorageService.load("chat/" + cleanName(fileName));
    }

    @KafkaListener(topics = "${app.chat.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void persistAndBroadcast(ChatMessageEvent event) {
        Timer.Sample consumeSample = chatMetricsService.startTimer();
        try {
            if (chatMessageRepository.existsById(event.messageId())) {
                chatMetricsService.recordKafkaConsumeSuccess(consumeSample, event);
                log.debug("Skipped duplicate chat message event. messageId={}", event.messageId());
                return;
            }
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
                    event.replyToMessageId(),
                    event.replyToSenderName(),
                    event.replyToContent(),
                    event.createdAt()
            ));
            incrementUnreadForRecipients(event);
            Timer.Sample indexSample = chatMetricsService.startTimer();
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
                chatMetricsService.recordElasticsearchIndexSuccess(indexSample);
            } catch (RuntimeException exception) {
                chatMetricsService.recordElasticsearchIndexFailure(indexSample);
                log.warn("Unable to index chat message into Elasticsearch. MongoDB history remains available.", exception);
            }
            Timer.Sample broadcastSample = chatMetricsService.startTimer();
            try {
                messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId(), toMessageResponse(savedMessage, Map.of(), null));
                chatMetricsService.recordWebSocketBroadcastSuccess(broadcastSample);
            } catch (RuntimeException exception) {
                chatMetricsService.recordWebSocketBroadcastFailure(broadcastSample);
                throw exception;
            }
            notifyRecipients(event);
            chatMetricsService.recordKafkaConsumeSuccess(consumeSample, event);
        } catch (RuntimeException exception) {
            chatMetricsService.recordKafkaConsumeFailure(consumeSample, event);
            throw exception;
        }
    }

    private void incrementUnreadForRecipients(ChatMessageEvent event) {
        try {
            ChatRoom room = ensureRoom(event.roomId());
            Set<String> recipients = notificationRecipients(room, event.senderEmail());
            chatStateService.incrementUnread(event.roomId(), recipients);
        } catch (RuntimeException exception) {
            log.debug("Unable to update Redis unread counts. roomId={}", event.roomId(), exception);
        }
    }

    private void notifyRecipients(ChatMessageEvent event) {
        try {
            ChatRoom room = ensureRoom(event.roomId());
            Set<String> recipients = notificationRecipients(room, event.senderEmail());
            recipients.removeAll(mutedRecipientEmails(room.getId(), recipients));
            notificationService.createChatMessageNotifications(event, recipients);
        } catch (RuntimeException exception) {
            log.debug("Unable to create chat notifications. roomId={}", event.roomId(), exception);
        }
    }

    private void publishReadSummary(String roomId, RoomReadSummaryResponse readSummary) {
        try {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/read-receipts", readSummary);
        } catch (RuntimeException exception) {
            log.debug("Unable to publish read receipt update. roomId={}", roomId, exception);
        }
    }

    private Set<String> notificationRecipients(ChatRoom room, String senderEmail) {
        Set<String> recipients = participantEmailsForRoom(room);
        recipients.removeIf(email -> email.equalsIgnoreCase(senderEmail));
        return recipients;
    }

    private Set<String> mutedRecipientEmails(String roomId, Set<String> recipients) {
        if (recipients.isEmpty()) {
            return Set.of();
        }
        return chatRoomUserPreferenceRepository.findByRoomIdAndUserEmailIn(roomId, recipients)
                .stream()
                .filter(ChatRoomUserPreference::isMuted)
                .map(ChatRoomUserPreference::getUserEmail)
                .collect(java.util.stream.Collectors.toSet());
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

    private String normalizeReaction(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new IllegalArgumentException("반응 이모지가 필요합니다.");
        }
        String normalized = emoji.trim();
        if (normalized.length() > 16) {
            throw new IllegalArgumentException("반응 이모지는 16자 이하만 가능합니다.");
        }
        return normalized;
    }

    private Map<String, ChatRoomUserPreference> roomPreferencesMap(List<ChatRoom> rooms, String currentUserEmail) {
        if (rooms.isEmpty()) {
            return Map.of();
        }
        List<String> roomIds = rooms.stream().map(ChatRoom::getId).toList();
        return chatRoomUserPreferenceRepository.findByRoomIdInAndUserEmail(roomIds, currentUserEmail)
                .stream()
                .collect(java.util.stream.Collectors.toMap(ChatRoomUserPreference::getRoomId, preference -> preference));
    }

    private Comparator<ChatRoomResponse> roomResponseComparator() {
        return Comparator.comparing(ChatRoomResponse::pinned)
                .reversed()
                .thenComparing(ChatRoomResponse::createdAt, Comparator.reverseOrder());
    }

    private List<RoomParticipantResponse> participantResponses(ChatRoom room) {
        Set<String> emails = participantEmailsForRoom(room);
        if (emails.isEmpty()) {
            return List.of();
        }
        Map<String, UserAccount> usersByEmail = userAccountRepository.findByEmailIn(emails)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        user -> normalizeEmail(user.getEmail()),
                        user -> user,
                        (first, second) -> first
                ));
        return emails.stream()
                .map(this::normalizeEmail)
                .map(usersByEmail::get)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(UserAccount::getName))
                .map(participant -> new RoomParticipantResponse(
                        participant.getId(),
                        participant.getEmail(),
                        participant.getName(),
                        participant.getProvider().name(),
                        participant.getStatusMessage(),
                        participant.getProfileImageUrl(),
                        chatStateService.isOnline(participant.getEmail()),
                        participant.getEmail().equalsIgnoreCase(room.getCreatedBy())
                ))
                .toList();
    }

    private Set<String> participantEmailsForRoom(ChatRoom room) {
        if (room.getType() == ChatRoomType.GROUP && room.getParticipantEmails().isEmpty()) {
            return userAccountRepository.findAll()
                    .stream()
                    .map(UserAccount::getEmail)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }
        return new LinkedHashSet<>(room.getParticipantEmails());
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, String currentUserEmail) {
        ChatRoomUserPreference preference = chatRoomUserPreferenceRepository
                .findByRoomIdAndUserEmail(room.getId(), currentUserEmail)
                .orElse(null);
        return toRoomResponse(room, currentUserEmail, preference);
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room, String currentUserEmail, ChatRoomUserPreference preference) {
        return new ChatRoomResponse(
                room.getId(),
                room.getName(),
                room.getDescription(),
                room.getCreatedBy(),
                room.getType().name(),
                room.getCreatedAt(),
                chatStateService.unreadCount(room.getId(), currentUserEmail),
                preference != null && preference.isPinned(),
                preference != null && preference.isMuted(),
                participantEmailsForRoom(room).size()
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

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
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

    private ChatMessageResponse toMessageResponse(ChatMessageDocument message, Map<String, Instant> lastReadByEmail, String currentUserEmail) {
        long readCount = chatReadReceiptService.readCount(message, lastReadByEmail);
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
                message.getCreatedAt(),
                message.getEditedAt(),
                readCount,
                readCount > 0 ? "READ" : "SENT",
                toReactionResponses(message, currentUserEmail),
                message.getReplyToMessageId(),
                message.getReplyToSenderName(),
                message.getReplyToContent()
        );
    }

    private List<MessageReactionResponse> toReactionResponses(ChatMessageDocument message, String currentUserEmail) {
        return message.getReactionEmailsByEmoji()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> new MessageReactionResponse(
                        entry.getKey(),
                        entry.getValue().size(),
                        currentUserEmail != null && entry.getValue().stream().anyMatch(email -> email.equalsIgnoreCase(currentUserEmail)),
                        entry.getValue().stream().toList()
                ))
                .toList();
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
                message.getCreatedAt(),
                null,
                0,
                "SENT",
                List.of(),
                null,
                null,
                null
        );
    }

    private ChatMessageDocument resolveReplySource(String roomId, String replyToMessageId, UserAccount user) {
        if (replyToMessageId == null || replyToMessageId.isBlank()) {
            return null;
        }
        ChatMessageDocument replySource = ensureMessageInRoom(roomId, replyToMessageId);
        if (replySource.isDeletedForEveryone() || !replySource.isVisibleTo(user.getEmail())) {
            throw new IllegalArgumentException("답장할 수 없는 메시지입니다.");
        }
        return replySource;
    }

    private String replyPreview(ChatMessageDocument message) {
        String source = message.getContent();
        if ((source == null || source.isBlank()) && message.getAttachmentName() != null) {
            source = message.getAttachmentName();
        }
        if (source == null || source.isBlank()) {
            source = "첨부 메시지";
        }
        String normalized = source.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 80) + "...";
    }
}
