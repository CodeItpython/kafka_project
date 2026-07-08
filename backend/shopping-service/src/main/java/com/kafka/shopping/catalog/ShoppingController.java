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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog endpoints (no auth): categories, per-category feed, search, popular keywords. */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingController {
    private final ShoppingService shoppingService;
    private final ProductSearchService productSearchService;
    private final SearchLogService searchLogService;
    private final PopularKeywordService popularKeywordService;

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return shoppingService.categories();
    }

    @GetMapping("/feed")
    public List<ProductResponse> feed(
            @RequestParam @NotBlank String category,
            @RequestParam(defaultValue = "sim") String sort,
            @RequestParam(defaultValue = "20") int display,
            @RequestParam(defaultValue = "1") int start,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return shoppingService.feed(category, sort, display, start, refresh);
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
