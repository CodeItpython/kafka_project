package com.example.kafka.news;

import com.example.kafka.news.GgJobsApiClient.GgJobsResult;
import com.example.kafka.news.YouthDtos.YouthJob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경기 공공일자리(잡아바) 채용공고를 주기적으로 openapi.gg.go.kr에서 전량 받아 Elasticsearch에 재색인하는 배치.
 * 업스트림은 일 1회 갱신 + 총건수가 작아(수백 건) 전량 스냅샷 교체가 저렴하다.
 *
 * <p>기동 후 {@code initial-delay}(기본 8s)에 첫 실행, 이후 {@code interval}(기본 6h)마다 반복.
 * 미구성(키/서비스명 없음)이면 아무 것도 안 한다. ES/업스트림 장애는 흡수 — 기존 색인을 유지하고
 * 서빙({@code YouthService.jobs()})은 색인이 비면 라이브 API로 degrade하므로 배치 실패가 노출을 막지 않는다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobIndexService {
    // 호출당 pSize(업스트림 상한 1000 이내). 총건수가 이보다 크면 페이지를 이어 받는다.
    private static final int FETCH_PAGE_SIZE = 500;
    // 폭주 방지 상한(정상 데이터는 수백 건 = 1~2페이지).
    private static final int MAX_PAGES = 20;

    private final ElasticsearchOperations elasticsearchOperations;
    private final JobSearchRepository repository;
    private final GgJobsApiClient jobsClient;

    @Scheduled(
            initialDelayString = "${app.youth.jobs.reindex-initial-delay-ms:8000}",
            fixedDelayString = "${app.youth.jobs.reindex-interval-ms:21600000}")
    public void reindex() {
        if (!jobsClient.isConfigured()) {
            return;
        }
        try {
            ensureIndex();
            Instant now = Instant.now();
            List<JobDocument> documents = fetchAll(now);
            if (documents.isEmpty()) {
                // 업스트림이 0건/오류 → 기존 색인을 그대로 두고 다음 주기에 재시도(빈 목록으로 덮어쓰지 않음).
                log.info("GG jobs reindex: no rows fetched; keeping existing index");
                return;
            }
            // 전량 교체: 만료/삭제된 공고를 정리한다. 삭제~저장 사이 짧은 빈 창은 라이브 API 폴백이 커버.
            repository.deleteAll();
            repository.saveAll(documents);
            log.info("GG jobs reindexed into Elasticsearch: {} docs", documents.size());
        } catch (RuntimeException exception) {
            log.warn("GG jobs reindex skipped (Elasticsearch/upstream unavailable): {}", exception.getMessage());
        }
    }

    /** 전 페이지를 이어 받아 문서로 변환. 도중 오류가 나면 부분분을 버리고 빈 리스트(=기존 색인 유지)를 반환. */
    private List<JobDocument> fetchAll(Instant now) {
        List<JobDocument> documents = new ArrayList<>();
        int seq = 0;
        long total = Long.MAX_VALUE;
        for (int page = 1; page <= MAX_PAGES && (long) (page - 1) * FETCH_PAGE_SIZE < total; page++) {
            GgJobsResult response = jobsClient.fetchJobs(page, FETCH_PAGE_SIZE);
            if (!response.ok()) {
                log.warn("GG jobs reindex: page {} not ok ({}); aborting to keep existing index",
                        page, response.message());
                return List.of();
            }
            total = response.totalCount() > 0 ? response.totalCount() : response.items().size();
            if (response.items().isEmpty()) {
                break;
            }
            for (YouthJob job : response.items()) {
                documents.add(JobDocument.from(job, seq++, now));
            }
            if (response.items().size() < FETCH_PAGE_SIZE) {
                break;
            }
        }
        return documents;
    }

    /** 애노테이션 매핑으로 인덱스를 보장(없으면 생성). 이른 동적 자동생성으로 매핑이 틀어지는 것을 방지. */
    private void ensureIndex() {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(JobDocument.class);
        if (!indexOperations.exists()) {
            indexOperations.create();
        }
        indexOperations.putMapping(indexOperations.createMapping(JobDocument.class));
    }
}
