package com.example.kafka.news;

import java.util.Arrays;

/**
 * 청년 정책 지역 필터. 서울/경기만 지원(그 외 지방 제외), 전체는 필터 없음.
 * {@code ctpvNm}은 온통청년 포털 검색의 시도명(STDG_CTPV_NM) 파라미터 값이다.
 */
public enum Region {
    SEOUL("seoul", "서울", "서울특별시"),
    GYEONGGI("gyeonggi", "경기", "경기도"),
    ALL("all", "전체", "");

    private final String code;
    private final String label;
    private final String ctpvNm;

    Region(String code, String label, String ctpvNm) {
        this.code = code;
        this.label = label;
        this.ctpvNm = ctpvNm;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /** 포털 검색 STDG_CTPV_NM 값(전체는 빈 문자열). */
    public String ctpvNm() {
        return ctpvNm;
    }

    /** 알 수 없는/공백 코드는 ALL로(뉴스 카테고리의 fromCode 관용성과 동일). */
    public static Region fromCode(String code) {
        if (code == null || code.isBlank()) {
            return ALL;
        }
        return Arrays.stream(values())
                .filter(region -> region.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(ALL);
    }
}
