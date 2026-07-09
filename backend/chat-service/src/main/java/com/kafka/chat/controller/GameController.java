package com.kafka.chat.controller;

import com.kafka.chat.dto.GameDtos.GameScoreResponse;
import com.kafka.chat.dto.GameDtos.SubmitScoreRequest;
import com.kafka.chat.dto.GameDtos.SubmitScoreResponse;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.service.GameService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 게임 개인 최고점수 API. 인증된 사용자(email) 기준. 턴제 대결(방 단위)은 후속 단계. */
@RestController
@RequestMapping("/api/chat/games")
@RequiredArgsConstructor
public class GameController {
    private final GameService gameService;

    @PostMapping("/scores")
    public ResponseEntity<SubmitScoreResponse> submit(
            @Valid @RequestBody SubmitScoreRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(gameService.submitScore(user.getEmail(), request.game(), request.score()));
    }

    @GetMapping("/scores/me")
    public ResponseEntity<List<GameScoreResponse>> myScores(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(gameService.myScores(user.getEmail()));
    }
}
