package com.kafka.auth.chat.service;

import com.kafka.auth.chat.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal, dependency-light room access check used by the STOMP interceptor.
 * Kept separate from {@link ChatService} so the WebSocket inbound-channel
 * interceptor does not pull in SimpMessagingTemplate and create a startup cycle.
 * Runs in a read-only transaction so the room's lazy participant/hidden
 * collections can be evaluated by {@code ChatRoom#isVisibleTo}.
 */
@Service
@RequiredArgsConstructor
public class ChatRoomAccessService {
    private final ChatRoomRepository chatRoomRepository;

    @Transactional(readOnly = true)
    public boolean canAccessRoom(String roomId, String email) {
        if (roomId == null || roomId.isBlank() || email == null || email.isBlank()) {
            return false;
        }
        return chatRoomRepository.findById(roomId)
                .map(room -> room.isVisibleTo(email))
                .orElse(false);
    }
}
