package com.kafka.shopping.catalog;

import com.kafka.shopping.catalog.ShoppingDtos.CategoryResponse;
import com.kafka.shopping.catalog.ShoppingDtos.PopularKeywordResponse;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.search.PopularKeywordService;
import com.kafka.shopping.search.ProductSearchService;
import com.kafka.shopping.search.SearchLogService;
import com.kafka.shopping.security.AuthUser;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

/** Public catalog endpoints (no auth): categories, per-category feed, search, popular keywords. */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
@Slf4j
public class ShoppingController {
    private final ShoppingService shoppingService;
    private final ProductSearchService productSearchService;
    private final SearchLogService searchLogService;
    private final PopularKeywordService popularKeywordService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return shoppingService.categories();
    }

    /**
     * 카테고리 피드. 기본은 네이버 실시간 조회이지만, 업스트림 장애(쇼핑 검색 API 차단·5xx 등)로 실패하면
     * 배치가 채워둔 Elasticsearch 카탈로그 색인으로 폴백한다 — 외부 API가 죽어도 마지막 색인분은 계속 서빙된다.
     * (알 수 없는 카테고리는 그대로 400으로 남긴다: 업스트림 장애가 아니라 잘못된 요청이므로.)
     */
    @GetMapping("/feed")
    public List<ProductResponse> feed(
            @RequestParam @NotBlank String category,
            @RequestParam(defaultValue = "sim") String sort,
            @RequestParam(defaultValue = "20") int display,
            @RequestParam(defaultValue = "1") int start,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        try {
            return shoppingService.feed(category, sort, display, start, refresh);
        } catch (RestClientException upstreamFailure) {
            // 업스트림이 죽은 상황이므로 ES 색인만 본다. productSearchService.search 는 색인이 비면
            // 다시 업스트림으로 폴백해 같은 예외를 던지므로 이 경로에서는 쓰지 않는다.
            // 색인까지 비면 빈 목록 → 프론트가 "표시할 상품이 없습니다"로 안내(500 대신).
            // (여기 도달했다면 category 는 이미 feed() 에서 검증됨 — 잘못된 값은 400으로 먼저 걸린다)
            List<ProductResponse> indexed = productSearchService.byCategoryCode(category, sort, display, start);
            log.warn("Shopping feed: 네이버 조회 실패 → ES 색인 폴백 (category={}, 색인 {}건): {}",
                    category, indexed.size(), upstreamFailure.getMessage());
            return indexed;
        }
    }

    /** Elasticsearch-backed product search (falls back to Naver when the index is cold). */
    @GetMapping("/search")
    public List<ProductResponse> search(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "sim") String sort,
            @RequestParam(defaultValue = "20") int display,
            @RequestParam(defaultValue = "1") int start
    ) {
        List<ProductResponse> results = productSearchService.search(query, sort, display, start);
        // Count only the first page as one search so infinite-scroll paging isn't over-counted.
        if (start <= 1) {
            searchLogService.logQuietly(query, currentUserEmail(), results.size(), null);
        }
        return results;
    }

    /** 자동완성: 입력 중인 prefix로 상품 제목을 bool_prefix 매칭한 후보(Elasticsearch search_as_you_type). */
    @GetMapping("/suggest")
    public List<String> suggest(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "8") int size
    ) {
        return productSearchService.suggest(query, size);
    }

    /** 연관검색어: 시드 검색어와 함께 자주 등장하는 상품 키워드(Elasticsearch significant_text). */
    @GetMapping("/related")
    public List<String> related(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "8") int size
    ) {
        return productSearchService.relatedKeywords(query, size);
    }

    @GetMapping("/popular-keywords")
    public List<PopularKeywordResponse> popularKeywords() {
        return popularKeywordService.top();
    }

    private String currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof AuthUser user ? user.getEmail() : null;
    }
}
