package com.example.kafka.news;

import java.util.List;

public final class NewsDtos {
    private NewsDtos() {
    }

    /** 개별 뉴스 카드. thumbnail/press는 없을 수 있어 null 허용. */
    public record NewsItem(
            String id,
            String title,
            String url,
            String press,
            String thumbnail
    ) {
    }

    /** 카테고리 탭 목록 응답 */
    public record CategoryResponse(String code, String label) {
    }

    /** 피드 응답 */
    public record FeedResponse(
            String category,
            String label,
            int count,
            List<NewsItem> items
    ) {
    }
}
