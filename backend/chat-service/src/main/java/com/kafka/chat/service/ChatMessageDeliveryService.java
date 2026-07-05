package com.kafka.chat.service;

import com.kafka.chat.model.ChatMessageDeliveryState;
import com.kafka.chat.model.ChatMessageDeliveryStatus;
import com.kafka.chat.model.ChatMessageDocument;
import com.kafka.chat.repository.ChatMessageDeliveryStateRepository;
import com.kafka.chat.security.AuthUser;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageDeliveryService {
    private final ChatMessageDeliveryStateRepository chatMessageDeliveryStateRepository;

    @Transactional
    public void createSentStates(ChatMessageDocument message, Set<String> recipientEmails) {
        if (recipientEmails.isEmpty()) {
            return;
        }
        Set<String> existingEmails = chatMessageDeliveryStateRepository.findByMessageIdIn(List.of(message.getId()))
                .stream()
                .filter(state -> state.getMessageId().equals(message.getId()))
                .map(state -> state.getUserEmail().toLowerCase())
                .collect(Collectors.toCollection(HashSet::new));
        List<ChatMessageDeliveryState> states = recipientEmails.stream()
                .filter(email -> !email.equalsIgnoreCase(message.getSenderEmail()))
                .filter(email -> existingEmails.add(email.toLowerCase()))
                .map(email -> new ChatMessageDeliveryState(message.getId(), message.getRoomId(), email))
                .toList();
        if (!states.isEmpty()) {
            chatMessageDeliveryStateRepository.saveAll(states);
        }
    }

    @Transactional
    public DeliverySnapshot markDelivered(ChatMessageDocument message, AuthUser user, Map<String, Instant> lastReadByEmail) {
        if (!message.getSenderEmail().equalsIgnoreCase(user.getEmail())) {
            ChatMessageDeliveryState state = chatMessageDeliveryStateRepository
                    .findByMessageIdAndUserEmail(message.getId(), user.getEmail())
                    .orElseGet(() -> new ChatMessageDeliveryState(message.getId(), message.getRoomId(), user.getEmail()));
            state.markDelivered(Instant.now());
            chatMessageDeliveryStateRepository.save(state);
        }
        return summary(message, lastReadByEmail);
    }

    @Transactional
    public void markRoomRead(String roomId, String userEmail, Instant readAt) {
        List<ChatMessageDeliveryState> states = chatMessageDeliveryStateRepository.findByRoomIdAndUserEmail(roomId, userEmail);
        states.forEach(state -> state.markRead(readAt));
        if (!states.isEmpty()) {
            chatMessageDeliveryStateRepository.saveAll(states);
        }
    }

    @Transactional(readOnly = true)
    public DeliverySnapshot summary(ChatMessageDocument message, Map<String, Instant> lastReadByEmail) {
        return summaries(List.of(message), lastReadByEmail).getOrDefault(
                message.getId(),
                new DeliverySnapshot(message.getId(), 0, 0, ChatMessageDeliveryStatus.SENT.name())
        );
    }

    @Transactional(readOnly = true)
    public Map<String, DeliverySnapshot> summaries(Collection<ChatMessageDocument> messages, Map<String, Instant> lastReadByEmail) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        Map<String, ChatMessageDocument> messagesById = messages.stream()
                .collect(Collectors.toMap(
                        ChatMessageDocument::getId,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        Map<String, Long> deliveredCounts = chatMessageDeliveryStateRepository.findByMessageIdIn(messagesById.keySet())
                .stream()
                .filter(state -> state.getStatus() == ChatMessageDeliveryStatus.DELIVERED || state.getStatus() == ChatMessageDeliveryStatus.READ)
                .collect(Collectors.groupingBy(ChatMessageDeliveryState::getMessageId, Collectors.counting()));

        Map<String, DeliverySnapshot> snapshots = new LinkedHashMap<>();
        messagesById.forEach((messageId, message) -> {
            long readCount = readCount(message, lastReadByEmail);
            long deliveredCount = deliveredCounts.getOrDefault(messageId, 0L);
            String deliveryStatus = deliveryStatus(readCount, deliveredCount);
            snapshots.put(messageId, new DeliverySnapshot(messageId, deliveredCount, readCount, deliveryStatus));
        });
        return snapshots;
    }

    private long readCount(ChatMessageDocument message, Map<String, Instant> lastReadByEmail) {
        if (message.getCreatedAt() == null) {
            return 0;
        }
        return lastReadByEmail.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase(message.getSenderEmail()))
                .filter(entry -> !entry.getValue().isBefore(message.getCreatedAt()))
                .count();
    }

    private String deliveryStatus(long readCount, long deliveredCount) {
        if (readCount > 0) {
            return ChatMessageDeliveryStatus.READ.name();
        }
        if (deliveredCount > 0) {
            return ChatMessageDeliveryStatus.DELIVERED.name();
        }
        return ChatMessageDeliveryStatus.SENT.name();
    }

    public record DeliverySnapshot(
            String messageId,
            long deliveredCount,
            long readCount,
            String deliveryStatus
    ) {
    }
}
