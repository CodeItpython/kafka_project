package com.example.kafka.news;

import java.util.Arrays;
import java.util.Optional;

/**
 * 네이버 뉴스 카테고리와 크롤링 대상 URL.
 * code는 프론트/API에서 사용하는 식별자, label은 화면 표시명.
 */
public enum NewsCategory {
    HOT("hot", "실시간 핫", "https://news.naver.com/main/ranking/popularDay.naver"),
    ECONOMY("economy", "경제", "https://news.naver.com/section/101"),
    STOCK("stock", "증시", "https://news.naver.com/breakingnews/section/101/258"),
    SOCIETY("society", "사회", "https://news.naver.com/section/102"),
    IT("it", "IT·과학", "https://news.naver.com/section/105"),
    WORLD("world", "세계", "https://news.naver.com/section/104");

    private final String code;
    private final String label;
    private final String url;

    NewsCategory(String code, String label, String url) {
        this.code = code;
        this.label = label;
        this.url = url;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String url() {
        return url;
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
