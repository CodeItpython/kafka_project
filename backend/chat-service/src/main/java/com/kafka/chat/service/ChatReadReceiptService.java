package com.kafka.chat.service;

import com.kafka.chat.dto.ChatDtos.ReadReceiptResponse;
import com.kafka.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.chat.model.ChatMessageDocument;
import com.kafka.chat.model.ChatRoom;
import com.kafka.chat.model.ChatRoomReadState;
import com.kafka.chat.model.ChatRoomType;
import com.kafka.chat.repository.ChatRoomReadStateRepository;
import com.kafka.chat.client.UserDirectoryClient;
import com.kafka.chat.client.UserView;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.storage.StorageUrlSigner;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
public class ChatReadReceiptService {
    private final ChatRoomReadStateRepository chatRoomReadStateRepository;
    private final UserDirectoryClient userDirectory;
    private final ChatStateService chatStateService;
    private final ChatMessageDeliveryService chatMessageDeliveryService;
    private final StorageUrlSigner storageUrlSigner;

    @Transactional
    public RoomReadSummaryResponse markRead(ChatRoom room, AuthUser user, Instant readAt) {
        ChatRoomReadState state = chatRoomReadStateRepository.findByRoomIdAndUserEmail(room.getId(), user.getEmail())
                .orElseGet(() -> new ChatRoomReadState(room.getId(), user.getEmail(), readAt));
        state.markRead(readAt);
        chatRoomReadStateRepository.save(state);
        chatMessageDeliveryService.markRoomRead(room.getId(), user.getEmail(), readAt);
        chatStateService.markRoomRead(room.getId(), user.getEmail());
        return summary(room, user);
    }

    @Transactional(readOnly = true)
    public RoomReadSummaryResponse summary(ChatRoom room, AuthUser currentUser) {
        Map<String, UserView> usersByEmail = roomParticipants(room)
                .stream()
                .collect(Collectors.toMap(
                        UserView::getEmail,
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        Map<String, Instant> lastReadByEmail = chatRoomReadStateRepository.findByRoomIdAndUserEmailIn(room.getId(), usersByEmail.keySet())
                .stream()
                .collect(Collectors.toMap(
                        state -> state.getUserEmail().toLowerCase(),
                        ChatRoomReadState::getLastReadAt,
                        (first, second) -> first
                ));
        List<ReadReceiptResponse> receipts = usersByEmail.values()
                .stream()
                .map(user -> new ReadReceiptResponse(
                        user.getEmail(),
                        user.getName(),
                        storageUrlSigner.sign(user.getProfileImageUrl()),
                        chatStateService.isOnline(user.getEmail()),
                        lastReadByEmail.get(user.getEmail().toLowerCase())
                ))
                .toList();
        Instant currentUserLastReadAt = lastReadByEmail.get(currentUser.getEmail().toLowerCase());
        return new RoomReadSummaryResponse(room.getId(), currentUserLastReadAt, receipts);
    }

    @Transactional(readOnly = true)
    public Map<String, Instant> lastReadByEmail(String roomId) {
        return chatRoomReadStateRepository.findByRoomId(roomId)
                .stream()
                .collect(Collectors.toMap(
                        state -> state.getUserEmail().toLowerCase(),
                        ChatRoomReadState::getLastReadAt,
                        (first, second) -> first
                ));
    }

    public long readCount(ChatMessageDocument message, Map<String, Instant> lastReadByEmail) {
        if (message.getCreatedAt() == null) {
            return 0;
        }
        return lastReadByEmail.entrySet()
                .stream()
                .filter(entry -> !entry.getKey().equalsIgnoreCase(message.getSenderEmail()))
                .filter(entry -> !entry.getValue().isBefore(message.getCreatedAt()))
                .count();
    }

    private List<UserView> roomParticipants(ChatRoom room) {
        if (room.getType() == ChatRoomType.DIRECT) {
            return userDirectory.findByEmails(new LinkedHashSet<>(room.getParticipantEmails()));
        }
        return userDirectory.search(null, 30);
    }
}
