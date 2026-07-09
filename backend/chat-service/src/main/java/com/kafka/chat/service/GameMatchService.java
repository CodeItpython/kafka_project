package com.kafka.chat.service;

import com.kafka.chat.dto.GameDtos.GameMatchResponse;
import com.kafka.chat.model.ChatRoom;
import com.kafka.chat.model.GameKind;
import com.kafka.chat.model.GameMatch;
import com.kafka.chat.repository.ChatRoomRepository;
import com.kafka.chat.repository.GameMatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챗방 턴제 게임 대결. 도전자가 대결을 만들고 한 판, 상대가 한 판 플레이해 점수로 겨룬다.
 * 진행 이벤트는 방 서브토픽 {@code /topic/rooms/{roomId}/game}으로 브로드캐스트(채팅 메시지 채널과 분리).
 */
@Service
@RequiredArgsConstructor
public class GameMatchService {
    private final GameMatchRepository matchRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public GameMatchResponse create(String roomId, String email, String name, GameKind game) {
        requireMember(roomId, email);
        GameMatch match = matchRepository.save(new GameMatch(roomId, game, email, displayName(name, email)));
        return broadcast(match);
    }

    @Transactional
    public GameMatchResponse submitRound(String roomId, Long matchId, String email, String name, int score) {
        requireMember(roomId, email);
        int safeScore = Math.max(0, score);
        GameMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("대결을 찾을 수 없습니다."));
        if (!match.getRoomId().equals(roomId)) {
            throw new IllegalArgumentException("대결이 이 채팅방에 속하지 않습니다.");
        }
        switch (match.getStatus()) {
            case WAITING_CHALLENGER -> {
                if (!match.getChallengerEmail().equals(email)) {
                    throw new IllegalStateException("도전자가 먼저 플레이해야 합니다.");
                }
                match.challengerRound(safeScore);
            }
            case WAITING_OPPONENT -> {
                if (match.getChallengerEmail().equals(email)) {
                    throw new IllegalStateException("상대가 플레이할 차례입니다.");
                }
                match.opponentRound(email, displayName(name, email), safeScore);
            }
            default -> throw new IllegalStateException("이미 끝난 대결입니다.");
        }
        matchRepository.save(match);
        return broadcast(match);
    }

    @Transactional(readOnly = true)
    public GameMatchResponse latest(String roomId, String email) {
        requireMember(roomId, email);
        return matchRepository.findFirstByRoomIdOrderByCreatedAtDesc(roomId)
                .map(GameMatchService::toResponse)
                .orElse(null);
    }

    private void requireMember(String roomId, String email) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
        if (!room.isVisibleTo(email)) {
            throw new IllegalStateException("채팅방 참여자만 이용할 수 있습니다.");
        }
    }

    private GameMatchResponse broadcast(GameMatch match) {
        GameMatchResponse response = toResponse(match);
        messagingTemplate.convertAndSend("/topic/rooms/" + match.getRoomId() + "/game", response);
        return response;
    }

    private static String displayName(String name, String email) {
        return (name == null || name.isBlank()) ? email : name;
    }

    static GameMatchResponse toResponse(GameMatch match) {
        return new GameMatchResponse(
                match.getId(),
                match.getRoomId(),
                match.getGame(),
                match.getStatus(),
                match.getChallengerEmail(),
                match.getChallengerName(),
                match.getChallengerScore(),
                match.getOpponentEmail(),
                match.getOpponentName(),
                match.getOpponentScore(),
                match.winnerEmail()
        );
    }
}
