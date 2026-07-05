package com.example.kafka.news;

import com.example.kafka.news.NewsDtos.CategoryResponse;
import com.example.kafka.news.NewsDtos.FeedResponse;
import com.example.kafka.news.NewsDtos.NewsItem;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
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
    public FeedResponse feed(@RequestParam(name = "category", required = false) String category) {
        NewsCategory resolved = NewsCategory.fromCode(category);
        List<NewsItem> items = newsService.feed(resolved);
        return new FeedResponse(resolved.code(), resolved.label(), items.size(), items);
    }
}
