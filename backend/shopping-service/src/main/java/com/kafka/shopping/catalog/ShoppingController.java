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
