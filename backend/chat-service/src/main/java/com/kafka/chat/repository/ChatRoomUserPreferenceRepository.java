package com.kafka.chat.repository;

import com.kafka.chat.model.ChatRoomUserPreference;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomUserPreferenceRepository extends JpaRepository<ChatRoomUserPreference, Long> {
    Optional<ChatRoomUserPreference> findByRoomIdAndUserEmail(String roomId, String userEmail);

    List<ChatRoomUserPreference> findByRoomIdInAndUserEmail(Collection<String> roomIds, String userEmail);

    List<ChatRoomUserPreference> findByRoomIdAndUserEmailIn(String roomId, Collection<String> userEmails);
}
