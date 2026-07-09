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
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗방 턴제 게임 대결. 도전자가 한 판, 상대가 한 판 플레이하고 점수로 승부한다(비동기).
 * 상태 흐름: WAITING_CHALLENGER → (도전자 제출) → WAITING_OPPONENT → (상대 제출) → DONE.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "game_matches",
        indexes = @Index(name = "idx_game_match_room", columnList = "room_id, created_at")
)
public class GameMatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameKind game;

    @Column(name = "challenger_email", nullable = false, length = 320)
    private String challengerEmail;

    @Column(name = "challenger_name", nullable = false, length = 120)
    private String challengerName;

    @Column(name = "challenger_score")
    private Integer challengerScore;

    @Column(name = "opponent_email", length = 320)
    private String opponentEmail;

    @Column(name = "opponent_name", length = 120)
    private String opponentName;

    @Column(name = "opponent_score")
    private Integer opponentScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.WAITING_CHALLENGER;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public GameMatch(String roomId, GameKind game, String challengerEmail, String challengerName) {
        this.roomId = roomId;
        this.game = game;
        this.challengerEmail = challengerEmail;
        this.challengerName = challengerName;
    }

    public void challengerRound(int score) {
        this.challengerScore = score;
        this.status = MatchStatus.WAITING_OPPONENT;
        this.updatedAt = Instant.now();
    }

    public void opponentRound(String email, String name, int score) {
        this.opponentEmail = email;
        this.opponentName = name;
        this.opponentScore = score;
        this.status = MatchStatus.DONE;
        this.updatedAt = Instant.now();
    }

    /** 승자 이메일. 무승부/미완료면 null. */
    public String winnerEmail() {
        if (status != MatchStatus.DONE || challengerScore == null || opponentScore == null) {
            return null;
        }
        if (challengerScore > opponentScore) {
            return challengerEmail;
        }
        if (opponentScore > challengerScore) {
            return opponentEmail;
        }
        return null;
    }
}
