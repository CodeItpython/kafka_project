package com.kafka.chat.model;

/** 지원 게임 종류. DB에는 이름(문자열)으로 저장되며 game_scores.ck_game_score_game CHECK와 일치해야 한다. */
public enum GameKind {
    SNAKE,
    TETRIS,
    G2048
}
