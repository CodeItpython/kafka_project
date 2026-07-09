package com.example.kafka.news;

import java.util.List;

public final class YouthDtos {
    private YouthDtos() {
    }

    /**
     * 청년 정책 카드. 일부 필드는 없을 수 있어 null 허용. id는 정책번호(plcyNo)로 프론트 무한스크롤 dedupe 키.
     */
    public record YouthPolicy(
            String id,
            String title,
            String summary,
            String support,
            List<String> keywords,
            String category,
            String subCategory,
            String region,
            String agency,
            Integer minAge,
            Integer maxAge,
            String applyUrl,
            String refUrl,
            String period
    ) {
    }

    /**
     * 정책 목록 응답. available=false면 "키 미설정"(결과 없음과 구분) — 프론트가 안내 문구를 다르게 낼 수 있다.
     * hasMore는 원본 페이지가 꽉 찼는지로 계산(지역 후처리로 짧아진 목록/totalCount 혼동 방지).
     */
    public record YouthPolicyResponse(
            String region,
            String category,
            String keyword,
            int page,
            int size,
            int totalCount,
            int count,
            boolean hasMore,
            boolean available,
            List<YouthPolicy> items
    ) {
    }
}
