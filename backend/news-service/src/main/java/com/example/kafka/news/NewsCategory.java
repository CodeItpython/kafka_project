package com.example.kafka.news;

import java.util.Arrays;
import java.util.Optional;

/**
 * 뉴스 카테고리. 네이버 뉴스 검색 API로 전환하면서 각 카테고리는 검색 키워드(query)에 매핑된다.
 * code는 프론트/API 식별자, label은 화면 표시명.
 */
public enum NewsCategory {
    HOT("hot", "실시간 핫", "속보"),
    ECONOMY("economy", "경제", "경제"),
    STOCK("stock", "증시", "증시"),
    SOCIETY("society", "사회", "사회"),
    IT("it", "IT·과학", "IT"),
    WORLD("world", "세계", "국제");

    private final String code;
    private final String label;
    private final String query;

    NewsCategory(String code, String label, String query) {
        this.code = code;
        this.label = label;
        this.query = query;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String query() {
        return query;
    }

    public static NewsCategory fromCode(String code) {
        return byCode(code).orElse(HOT);
    }

    public static Optional<NewsCategory> byCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(category -> category.code.equalsIgnoreCase(code.trim()))
                .findFirst();
    }
}
