package com.kafka.shopping.catalog;

import com.kafka.shopping.catalog.ShoppingDtos.CategoryResponse;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog endpoints (no auth): categories, per-category feed, free search. */
@RestController
@RequestMapping("/api/shopping")
@RequiredArgsConstructor
public class ShoppingController {
    private final ShoppingService shoppingService;

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

    @GetMapping("/search")
    public List<ProductResponse> search(
            @RequestParam @NotBlank String query,
            @RequestParam(defaultValue = "sim") String sort,
            @RequestParam(defaultValue = "20") int display,
            @RequestParam(defaultValue = "1") int start,
            @RequestParam(defaultValue = "false") boolean refresh
    ) {
        return shoppingService.search(query, sort, display, start, refresh);
    }
}
