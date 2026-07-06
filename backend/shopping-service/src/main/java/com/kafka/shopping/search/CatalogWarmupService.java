package com.kafka.shopping.search;

import com.kafka.shopping.catalog.ShoppingCategory;
import com.kafka.shopping.catalog.ShoppingService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Pre-populates and refreshes the product index by pulling each category feed from Naver
 * (which indexes as a side-effect of the fetch). Runs once after startup and then periodically
 * so prices/new items stay reasonably fresh. Stops early if Naver isn't configured.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogWarmupService {
    private final ShoppingService shoppingService;

    @Async
    public void warmAsync() {
        warm();
    }

    @Scheduled(
            initialDelayString = "${app.search.warm-interval-minutes:30}",
            fixedDelayString = "${app.search.warm-interval-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public void scheduledWarm() {
        warm();
    }

    private void warm() {
        int total = 0;
        for (ShoppingCategory category : ShoppingCategory.values()) {
            try {
                total += shoppingService.feed(category.code(), "sim", 100, 1, true).size();
            } catch (RuntimeException exception) {
                log.debug("Catalog warming stopped at {}: {}", category.code(), exception.getMessage());
                return;
            }
        }
        if (total > 0) {
            log.info("Warmed {} products into the Elasticsearch shopping index.", total);
        }
    }
}
