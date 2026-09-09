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

    /**
     * 인앱 리더용 기사 본문 블록. 원본 HTML을 그대로 내려주지 않고 문단/이미지로 구조화해
     * 클라이언트가 안전하게(=dangerouslySetInnerHTML 없이) 렌더할 수 있게 한다.
     * type: "p"(문단, text 사용) | "img"(이미지, src 사용)
     */
    public record ArticleBlock(String type, String text, String src) {
    }

    /** 인앱 리더 기사. blocks가 비면 추출 실패 → 프론트는 원문 링크로 안내한다. */
    public record Article(
            String url,
            String title,
            String siteName,
            String publishedAt,
            String leadImage,
            List<ArticleBlock> blocks
    ) {
    }
}
