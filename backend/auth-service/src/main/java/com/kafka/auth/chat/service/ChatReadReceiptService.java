package com.kafka.auth.chat.service;

import com.kafka.auth.chat.dto.ChatDtos.ReadReceiptResponse;
import com.kafka.auth.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.auth.chat.model.ChatMessageDocument;
import com.kafka.auth.chat.model.ChatRoom;
import com.kafka.auth.chat.model.ChatRoomReadState;
import com.kafka.auth.chat.model.ChatRoomType;
import com.kafka.auth.chat.repository.ChatRoomReadStateRepository;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.UserAccountRepository;
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
    private final UserAccountRepository userAccountRepository;
    private final ChatStateService chatStateService;

    @Transactional
    public RoomReadSummaryResponse markRead(ChatRoom room, UserAccount user, Instant readAt) {
        ChatRoomReadState state = chatRoomReadStateRepository.findByRoomIdAndUserEmail(room.getId(), user.getEmail())
                .orElseGet(() -> new ChatRoomReadState(room.getId(), user.getEmail(), readAt));
        state.markRead(readAt);
        chatRoomReadStateRepository.save(state);
        chatStateService.markRoomRead(room.getId(), user.getEmail());
        return summary(room, user);
    }

    @Transactional(readOnly = true)
    public RoomReadSummaryResponse summary(ChatRoom room, UserAccount currentUser) {
        Map<String, UserAccount> usersByEmail = roomParticipants(room)
                .stream()
                .collect(Collectors.toMap(
                        UserAccount::getEmail,
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
                        user.getProfileImageUrl(),
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

    private List<UserAccount> roomParticipants(ChatRoom room) {
        if (room.getType() == ChatRoomType.DIRECT) {
            Set<String> emails = new LinkedHashSet<>(room.getParticipantEmails());
            return userAccountRepository.findAll()
                    .stream()
                    .filter(user -> emails.contains(user.getEmail()))
                    .toList();
        }
        return userAccountRepository.findTop30ByOrderByNameAsc();
    }
}
