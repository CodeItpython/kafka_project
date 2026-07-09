package com.kafka.chat.service;

import com.kafka.chat.dto.GameDtos.GameScoreResponse;
import com.kafka.chat.dto.GameDtos.SubmitScoreResponse;
import com.kafka.chat.model.GameKind;
import com.kafka.chat.model.GameScore;
import com.kafka.chat.repository.GameScoreRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게임 개인 최고점수. 점수는 클라이언트가 제출하므로 위·변조 가능하다(재미 기능이므로 v1은 신뢰).
 * (user_email, game)당 한 행을 두고 더 높은 점수만 반영한다.
 */
@Service
@RequiredArgsConstructor
public class GameService {
    private final GameScoreRepository gameScoreRepository;

    @Transactional
    public SubmitScoreResponse submitScore(String userEmail, GameKind game, int score) {
        int safeScore = Math.max(0, score);
        GameScore record = gameScoreRepository.findByUserEmailAndGame(userEmail, game).orElse(null);
        boolean improved;
        if (record == null) {
            record = new GameScore(userEmail, game, safeScore);
            improved = true;
        } else {
            improved = record.submit(safeScore);
        }
        gameScoreRepository.save(record);
        return new SubmitScoreResponse(game, record.getBestScore(), improved);
    }

    @Transactional(readOnly = true)
    public List<GameScoreResponse> myScores(String userEmail) {
        return gameScoreRepository.findByUserEmail(userEmail).stream()
                .map(score -> new GameScoreResponse(score.getGame(), score.getBestScore(), score.getUpdatedAt()))
                .toList();
    }
}
