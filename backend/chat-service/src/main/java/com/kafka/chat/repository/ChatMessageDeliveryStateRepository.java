package com.kafka.chat.repository;

import com.kafka.chat.model.ChatMessageDeliveryState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageDeliveryStateRepository extends JpaRepository<ChatMessageDeliveryState, Long> {
    Optional<ChatMessageDeliveryState> findByMessageIdAndUserEmail(String messageId, String userEmail);

    List<ChatMessageDeliveryState> findByMessageIdIn(Collection<String> messageIds);

    List<ChatMessageDeliveryState> findByRoomIdAndUserEmail(String roomId, String userEmail);
}
