package com.kafka.shopping.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the search feature: Elasticsearch repositories (scoped to this package so the
 * JPA cart repository is untouched), config properties, and the async/scheduling
 * infrastructure used for non-blocking indexing and periodic catalog warming.
 */
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
@EnableElasticsearchRepositories(basePackageClasses = ProductSearchRepository.class)
@EnableAsync
@EnableScheduling
public class SearchConfig {
}
