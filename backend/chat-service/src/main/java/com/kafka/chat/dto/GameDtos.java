package com.kafka.chat.dto;

import com.kafka.chat.model.GameKind;
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
}
