package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.CategoryResponse;
import com.example.kafka.news.NewsDtos.FeedResponse;
import com.example.kafka.news.NewsDtos.LinkPreview;
import com.example.kafka.news.NewsDtos.NewsItem;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;
    private final LinkPreviewService linkPreviewService;

    public NewsController(NewsService newsService, LinkPreviewService linkPreviewService) {
        this.newsService = newsService;
        this.linkPreviewService = linkPreviewService;
    }

    /** 카테고리 탭 목록 */
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return Arrays.stream(NewsCategory.values())
                .map(category -> new CategoryResponse(category.code(), category.label()))
                .toList();
    }

    /** 카테고리별 뉴스 피드 (기본 실시간 핫) */
    @GetMapping("/feed")
    public FeedResponse feed(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "start", required = false, defaultValue = "1") int start,
            @RequestParam(name = "display", required = false, defaultValue = "20") int display,
            @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
    ) {
        NewsCategory resolved = NewsCategory.fromCode(category);
        List<NewsItem> items = newsService.feed(resolved, start, display, refresh);
        return new FeedResponse(resolved.code(), resolved.label(), items.size(), items);
    }

    /** 채팅 링크 공유용 Open Graph 미리보기. 미리보기를 만들 수 없으면 204. */
    @GetMapping("/link-preview")
    public ResponseEntity<LinkPreview> linkPreview(@RequestParam(name = "url") String url) {
        return linkPreviewService.preview(url)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
