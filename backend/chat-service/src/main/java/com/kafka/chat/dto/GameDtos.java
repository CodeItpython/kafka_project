package com.kafka.chat.dto;

import com.kafka.chat.model.GameKind;
import com.kafka.chat.model.MatchStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class GameDtos {
    private GameDtos() {
    }

    /** 게임 종료 시 점수 제출. */
    public record SubmitScoreRequest(@NotNull GameKind game, @Min(0) int score) {
    }

    /** 제출 결과: 반영된 최고점 + 이번에 갱신됐는지. */
    public record SubmitScoreResponse(GameKind game, int bestScore, boolean improved) {
    }

    /** 내 최고점 목록 항목. */
    public record GameScoreResponse(GameKind game, int bestScore, Instant updatedAt) {
    }

    /** 턴제 대결 생성 요청. */
    public record CreateMatchRequest(@NotNull GameKind game) {
    }

    /** 대결 라운드 점수 제출. */
    public record SubmitRoundRequest(@Min(0) int score) {
    }

    /** 대결 상태 브로드캐스트/응답. winnerEmail은 DONE·비무승부일 때만 값이 있다. */
    public record GameMatchResponse(
            Long id,
            String roomId,
            GameKind game,
            MatchStatus status,
            String challengerEmail,
            String challengerName,
            Integer challengerScore,
            String opponentEmail,
            String opponentName,
            Integer opponentScore,
            String winnerEmail
    ) {
    }
}
