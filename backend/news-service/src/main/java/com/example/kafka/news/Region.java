package com.example.kafka.news;

import java.util.Arrays;

/**
 * 청년 정책 지역 필터. 서울/경기만 지원(그 외 지방은 제외), 전체는 필터 없음.
 *
 * <p>온통청년 API의 {@code zipCd} 파라미터 동작이 불안정할 수 있어, 지역 판정의 <b>권위는 응답 후처리
 * ({@link #matches})</b>에 둔다. {@code zipPrefix}는 법정동코드 앞 2자리(서울 11, 경기 41)다.
 */
public enum Region {
    SEOUL("seoul", "서울", "11"),
    GYEONGGI("gyeonggi", "경기", "41"),
    ALL("all", "전체", null);

    private final String code;
    private final String label;
    private final String zipPrefix;

    Region(String code, String label, String zipPrefix) {
        this.code = code;
        this.label = label;
        this.zipPrefix = zipPrefix;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    /**
     * API의 {@code zipCd} 파라미터로 넘길 값. 현재는 항상 null(2자리 prefix는 유효 법정동코드가 아니므로
     * 서버측 필터를 신뢰하지 않고 응답 후처리로만 거른다). 실키 검증 후 유효 코드 목록을 붙일 seam.
     */
    public String zipParamOrNull() {
        return null;
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

    /**
     * 정책 1건이 이 지역에 속하는지. ALL은 항상 true. 그 외는 법정동코드 prefix 일치 OR 기관명에 지역명 포함.
     * (전국단위 정책은 zipCd가 비고 기관도 지역명이 없어 서울/경기에서는 제외 → 전체에만 노출)
     */
    public boolean matches(String zipCd, String rgtrInstCdNm, String sprvsnInstCdNm) {
        if (this == ALL) {
            return true;
        }
        if (zipCd != null && !zipCd.isBlank()) {
            for (String code : zipCd.split(",")) {
                if (code.trim().startsWith(zipPrefix)) {
                    return true;
                }
            }
        }
        return instContains(rgtrInstCdNm) || instContains(sprvsnInstCdNm);
    }

    private boolean instContains(String inst) {
        return inst != null && inst.contains(label);
    }
}
