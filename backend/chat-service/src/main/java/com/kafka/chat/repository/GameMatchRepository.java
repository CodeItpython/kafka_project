package com.kafka.chat.repository;

import com.kafka.chat.model.GameMatch;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameMatchRepository extends JpaRepository<GameMatch, Long> {
    Optional<GameMatch> findFirstByRoomIdOrderByCreatedAtDesc(String roomId);
}
