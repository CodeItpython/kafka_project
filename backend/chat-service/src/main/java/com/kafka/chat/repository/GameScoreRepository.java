package com.kafka.chat.repository;

import com.kafka.chat.model.GameKind;
import com.kafka.chat.model.GameScore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {
    Optional<GameScore> findByUserEmailAndGame(String userEmail, GameKind game);

    List<GameScore> findByUserEmail(String userEmail);
}
