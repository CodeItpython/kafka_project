package com.kafka.shopping.catalog;

import java.util.Arrays;
import java.util.Optional;

/**
 * The 8 curated shopping categories. Naver's API has no "hot deals" endpoint, so each
 * category maps to a representative search keyword; "특가" is surfaced by sorting
 * (e.g. price ascending) on the client's request.
 */
public enum ShoppingCategory {
    ELECTRONICS("electronics", "전자기기", "노트북"),
    MEALKIT("mealkit", "음식/밀키트", "밀키트"),
    APPLIANCES("appliances", "가전제품", "가전"),
    HOUSEHOLD("household", "생활용품", "생활용품"),
    FASHION("fashion", "패션의류", "패션"),
    BEAUTY("beauty", "뷰티", "화장품"),
    FOOD("food", "식품", "간식"),
    // "베스트셀러"는 주제어가 아니라 마케팅 문구라, 제목에 그 단어가 든 아무 분류 상품이나 걸렸다
    // (도서/취미 피드에 "베스트셀러 롱청치마"가 노출되던 원인). 실제 주제어로 교체.
    HOBBY("hobby", "도서/취미", "도서");

    private final String code;
    private final String label;
    private final String query;

    ShoppingCategory(String code, String label, String query) {
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


    public static Optional<ShoppingCategory> fromCode(String code) {
        return Arrays.stream(values()).filter(category -> category.code.equalsIgnoreCase(code)).findFirst();
    }
}
