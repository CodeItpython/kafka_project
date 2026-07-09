package com.kafka.chat.model;

/** 턴제 게임 대결 진행 상태. game_matches.ck_game_match_status CHECK와 일치해야 한다. */
public enum MatchStatus {
    WAITING_CHALLENGER,
    WAITING_OPPONENT,
    DONE
}
