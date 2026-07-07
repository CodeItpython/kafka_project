package com.kafka.shopping.batch;

import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.catalog.ShoppingService;
import com.kafka.shopping.search.ProductDocument;
import com.kafka.shopping.search.ProductSearchRepository;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch 카탈로그 색인 잡: Naver 상품(Reader) → ProductDocument(Processor) → Elasticsearch(Writer).
 * 청크 기반(50건)으로 커밋하고, ES 쓰기 실패는 Writer가 흡수해(베스트-에포트) 전체 잡이 죽지 않게 한다.
 * 실행 이력/재시작 메타데이터는 JobRepository(kafka_shopping의 BATCH_* 테이블)에 남는다.
 */
@Configuration
@Slf4j
public class CatalogIndexJobConfig {
    public static final String JOB_NAME = "catalogIndexJob";
    private static final int CHUNK_SIZE = 50;

    @Bean
    public Job catalogIndexJob(JobRepository jobRepository, Step indexProductsStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .start(indexProductsStep)
                .build();
    }

    @Bean
    public Step indexProductsStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            NaverProductItemReader naverProductItemReader,
            ItemProcessor<ProductResponse, ProductDocument> catalogItemProcessor,
            ItemWriter<ProductDocument> catalogItemWriter
    ) {
        return new StepBuilder("indexProductsStep", jobRepository)
                .<ProductResponse, ProductDocument>chunk(CHUNK_SIZE, transactionManager)
                .reader(naverProductItemReader)
                .processor(catalogItemProcessor)
                .writer(catalogItemWriter)
                .build();
    }

    @Bean
    @StepScope
    public NaverProductItemReader naverProductItemReader(ShoppingService shoppingService) {
        return new NaverProductItemReader(shoppingService);
    }

    @Bean
    public ItemProcessor<ProductResponse, ProductDocument> catalogItemProcessor() {
        return product -> {
            if (product.productId() == null || product.productId().isBlank()) {
                return null; // null 반환 = 해당 항목 필터링(색인 제외)
            }
            return ProductDocument.from(product, Instant.now());
        };
    }

    @Bean
    public ItemWriter<ProductDocument> catalogItemWriter(ProductSearchRepository repository) {
        return chunk -> {
            try {
                repository.saveAll(chunk.getItems());
                log.debug("Catalog index chunk written: {} products", chunk.size());
            } catch (RuntimeException exception) {
                // 색인은 베스트-에포트: ES 장애가 잡을 FAILED로 만들거나 항목별 재시도 폭주를 일으키지 않도록
                // 청크 실패를 흡수한다(다음 주기에 재시도). "색인이 서비스를 막지 않는다" 계약 유지.
                log.warn("Catalog index chunk write failed ({} products), skipping: {}", chunk.size(), exception.getMessage());
            }
        };
    }
}
