package com.kafka.chat.controller;

import com.kafka.chat.dto.GameDtos.CreateMatchRequest;
import com.kafka.chat.dto.GameDtos.GameMatchResponse;
import com.kafka.chat.dto.GameDtos.SubmitRoundRequest;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.service.GameMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 챗방 턴제 게임 대결 API. 진행 이벤트는 /topic/rooms/{roomId}/game 로 브로드캐스트된다. */
@RestController
@RequestMapping("/api/chat/rooms/{roomId}/game-matches")
@RequiredArgsConstructor
public class GameMatchController {
    private final GameMatchService gameMatchService;

    @PostMapping
    public ResponseEntity<GameMatchResponse> create(
            @PathVariable String roomId,
            @Valid @RequestBody CreateMatchRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(gameMatchService.create(roomId, user.getEmail(), user.getName(), request.game()));
    }

    @PostMapping("/{matchId}/rounds")
    public ResponseEntity<GameMatchResponse> submitRound(
            @PathVariable String roomId,
            @PathVariable Long matchId,
            @Valid @RequestBody SubmitRoundRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(gameMatchService.submitRound(roomId, matchId, user.getEmail(), user.getName(), request.score()));
    }

    @GetMapping("/latest")
    public ResponseEntity<GameMatchResponse> latest(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        GameMatchResponse response = gameMatchService.latest(roomId, user.getEmail());
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }
}
