package com.example.kafka.news;

import java.util.List;

public final class NewsDtos {
    private NewsDtos() {
    }

    /** 개별 뉴스 카드. thumbnail/press/description은 없을 수 있어 null 허용. */
    public record NewsItem(
            String id,
            String title,
            String url,
            String press,
            String thumbnail,
            String description
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

    /** 링크 미리보기 (Open Graph). 필드는 없을 수 있어 null 허용. */
    public record LinkPreview(
            String url,
            String title,
            String description,
            String image,
            String siteName
    ) {
        public boolean isEmpty() {
            return (title == null || title.isBlank())
                    && (description == null || description.isBlank())
                    && (image == null || image.isBlank());
        }
    }
}
