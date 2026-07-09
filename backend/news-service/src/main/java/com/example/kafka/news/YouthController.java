package com.example.kafka.news;

import com.example.kafka.news.YouthDtos.YouthPolicyResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 청년 '정책·혜택' API. 취업·소식은 별도 엔드포인트 없이 기존 /api/news/search를 재사용하므로 여기엔 정책만 있다.
 * CORS는 WebConfig의 /api/** 매핑으로 이미 커버된다.
 */
@RestController
@RequestMapping("/api/news/youth")
public class YouthController {

    private final YouthService youthService;

    public YouthController(YouthService youthService) {
        this.youthService = youthService;
    }

    /** 온통청년 청년정책 목록(서울/경기/전체). 키 미설정 시 200 + items:[] + available:false. */
    @GetMapping("/policies")
    public YouthPolicyResponse policies(
            @RequestParam(name = "region", required = false, defaultValue = "all") String region,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", required = false, defaultValue = "1") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh
    ) {
        Region resolved = Region.fromCode(region);
        YouthService.YouthResult result = youthService.policies(resolved, category, keyword, page, size, refresh);
        return new YouthPolicyResponse(
                resolved.code(),
                category == null ? "" : category.trim(),
                keyword == null ? "" : keyword.trim(),
                page,
                size,
                result.totalCount(),
                result.items().size(),
                result.hasMore(),
                youthService.isAvailable(),
                result.items()
        );
    }
}
