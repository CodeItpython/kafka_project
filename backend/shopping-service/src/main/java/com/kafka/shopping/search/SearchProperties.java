package com.kafka.shopping.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tunables for Elasticsearch-backed search, popular keywords, and catalog warming. */
@ConfigurationProperties(prefix = "app.search")
@Getter
@Setter
public class SearchProperties {
    /** Rolling window (hours) used to rank popular keywords. */
    private int popularWindowHours = 24;
    /** How many popular keywords to return (top N). */
    private int popularSize = 10;
    /** How long a computed popular-keyword ranking is cached before recompute. */
    private int popularCacheSeconds = 30;
    /** Whether to pre-populate the product index from Naver on startup. */
    private boolean warmOnStartup = true;
    /** Interval (minutes) for the periodic catalog re-warm. */
    private int warmIntervalMinutes = 30;
}
