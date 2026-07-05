package com.kafka.chat.repository;

import com.kafka.chat.model.ChatRoomReadState;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomReadStateRepository extends JpaRepository<ChatRoomReadState, Long> {
    Optional<ChatRoomReadState> findByRoomIdAndUserEmail(String roomId, String userEmail);

    List<ChatRoomReadState> findByRoomId(String roomId);

    List<ChatRoomReadState> findByRoomIdAndUserEmailIn(String roomId, Collection<String> userEmails);
}
