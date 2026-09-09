package com.kafka.shopping.catalog;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The 8 curated shopping categories. Naver's API has no "hot deals" endpoint, so each
 * category maps to a representative search keyword; "특가" is surfaced by sorting
 * (e.g. price ascending) on the client's request.
 */
public enum ShoppingCategory {
    ELECTRONICS("electronics", "전자기기", "노트북", List.of("디지털/가전")),
    MEALKIT("mealkit", "음식/밀키트", "밀키트", List.of("식품")),
    APPLIANCES("appliances", "가전제품", "가전", List.of("디지털/가전")),
    HOUSEHOLD("household", "생활용품", "생활용품", List.of("생활/건강")),
    FASHION("fashion", "패션의류", "패션", List.of("패션의류", "패션잡화")),
    BEAUTY("beauty", "뷰티", "화장품", List.of("화장품/미용")),
    FOOD("food", "식품", "간식", List.of("식품")),
    HOBBY("hobby", "도서/취미", "베스트셀러", List.of("여가/생활편의", "도서"));

    private final String code;
    private final String label;
    private final String query;
    private final List<String> naverCategories;

    ShoppingCategory(String code, String label, String query, List<String> naverCategories) {
        this.code = code;
        this.label = label;
        this.query = query;
        this.naverCategories = naverCategories;
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

    /**
     * 색인된 상품의 네이버 분류(category1) 값. 업스트림 장애로 ES 색인에서 서빙할 때
     * 대표 검색어(query)로 전문검색하면 엉뚱한 상품이 걸리므로(예: HOBBY의 "베스트셀러"가
     * 제목에 그 단어가 든 의류를 잡음) 이 분류로 필터한다.
     */
    public List<String> naverCategories() {
        return naverCategories;
    }

    public static Optional<ShoppingCategory> fromCode(String code) {
        return Arrays.stream(values()).filter(category -> category.code.equalsIgnoreCase(code)).findFirst();
    }
}
