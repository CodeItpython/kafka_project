package com.kafka.shopping.search;

import com.kafka.shopping.batch.CatalogIndexLauncher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * Creates the {@code shopping-products} and {@code shopping-searches} indices with the
 * annotation-derived mappings. If an index already exists with the wrong mapping (e.g. it was
 * dynamically auto-created by an early write before this ran), it is dropped and recreated so
 * {@code title} keeps its search_as_you_type type. Guarded so the service still boots if ES is
 * down — search then falls back to Naver until ES recovers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShoppingSearchIndexInitializer {
    private final ElasticsearchOperations elasticsearchOperations;
    private final SearchProperties properties;
    private final CatalogIndexLauncher catalogIndexLauncher;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        boolean productsReady = ensureIndex(ProductDocument.class, "title", "search_as_you_type");
        ensureIndex(SearchLogDocument.class, "keyword", "keyword");
        if (productsReady && properties.isWarmOnStartup()) {
            catalogIndexLauncher.launch("startup");
        }
    }

    private boolean ensureIndex(Class<?> documentClass, String field, String expectedType) {
        try {
            IndexOperations indexOperations = elasticsearchOperations.indexOps(documentClass);
            if (indexOperations.exists() && !hasFieldType(indexOperations.getMapping(), field, expectedType)) {
                log.info("Recreating Elasticsearch index with the expected mapping: {}", documentClass.getSimpleName());
                indexOperations.delete();
            }
            if (!indexOperations.exists()) {
                indexOperations.create();
            }
            indexOperations.putMapping(indexOperations.createMapping(documentClass));
            return true;
        } catch (RuntimeException exception) {
            log.warn("Elasticsearch index not ready ({}). It will be retried once ES is reachable. Cause: {}",
                    documentClass.getSimpleName(), exception.getMessage());
            return false;
        }
    }

    private boolean hasFieldType(Map<String, Object> mapping, String field, String expectedType) {
        Object properties = mapping.get("properties");
        if (!(properties instanceof Map<?, ?> propertiesMap)) {
            return false;
        }
        Object definition = propertiesMap.get(field);
        if (!(definition instanceof Map<?, ?> definitionMap)) {
            return false;
        }
        return expectedType.equals(definitionMap.get("type"));
    }
}
