package com.kafka.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별·게임별 개인 최고 점수. (user_email, game) 유니크로 게임마다 한 행만 유지하고 더 높은 점수만 반영한다.
 * (ChatRoomUserPreference와 동일한 관계형 영속화 패턴.)
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "game_scores",
        uniqueConstraints = @UniqueConstraint(name = "uk_game_score_user_game", columnNames = {"user_email", "game"}),
        indexes = @Index(name = "idx_game_score_user", columnList = "user_email")
)
public class GameScore {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameKind game;

    @Column(name = "best_score", nullable = false)
    private int bestScore;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public GameScore(String userEmail, GameKind game, int bestScore) {
        this.userEmail = userEmail;
        this.game = game;
        this.bestScore = bestScore;
    }

    /** 더 높은 점수일 때만 최고점을 갱신한다. 갱신되면 true. */
    public boolean submit(int score) {
        if (score > bestScore) {
            this.bestScore = score;
            this.updatedAt = Instant.now();
            return true;
        }
        return false;
    }
}
