package com.example.kafka.news;

import com.example.kafka.news.GgJobsApiClient.GgJobsResult;
import com.example.kafka.news.OntongYouthApiClient.PortalPolicyResponse;
import com.example.kafka.news.YouthDtos.YouthJob;
import com.example.kafka.news.YouthDtos.YouthPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 온통청년 청년정책 API 기반 정책 조회. (region,category,keyword,page,size)별로 TTL 캐시를 둔다.
 * 정책은 느리게 변하므로 뉴스(180s)보다 긴 기본 30분 TTL. 호출 실패 시 (있다면) 만료 캐시라도 반환한다.
 * ES/배치 없이 온디맨드 프록시로만 동작(기존 뉴스 피드 캐시 패턴 미러).
 */
@Slf4j
@Service
public class YouthService {
    private static final int MAX_CACHE_ENTRIES = 512;
    private static final int MAX_PAGE_SIZE = 100;

    private final OntongYouthApiClient client;
    private final GgJobsApiClient jobsClient;
    private final JobSearchRepository jobRepository;
    private final long ttlSeconds;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public YouthService(
            OntongYouthApiClient client,
            GgJobsApiClient jobsClient,
            JobSearchRepository jobRepository,
            @Value("${app.youth.cache-ttl-seconds:1800}") long ttlSeconds
    ) {
        this.client = client;
        this.jobsClient = jobsClient;
        this.jobRepository = jobRepository;
        this.ttlSeconds = ttlSeconds;
    }

    /** 정책 API 키가 설정됐는지(프론트가 "키 미설정" vs "결과 없음"을 구분하도록 컨트롤러가 노출). */
    public boolean isAvailable() {
        return client.isConfigured();
    }

    /** 경기 공공일자리(잡아바) API가 구성됐는지 — 미구성이면 프론트는 취업탭을 기존 뉴스로 유지. */
    public boolean isJobsAvailable() {
        return jobsClient.isConfigured();
    }

    /**
     * 경기 공공일자리 채용공고 조회. {@link JobIndexService}가 배치로 채워둔 Elasticsearch 색인에서 서빙하고
     * (재기동/업스트림 장애에 강함), 색인이 비었거나 ES가 없거나 강제 새로고침이면 라이브 API로 폴백한다.
     * 라이브 폴백이 업스트림 오류를 만나면 available=false로 낮춰(빈 목록을 성공으로 위장하지 않음) 프론트가
     * 기존 청년 취업 뉴스로 degrade하게 한다.
     */
    public JobResult jobs(int page, int size, boolean forceRefresh) {
        if (!jobsClient.isConfigured()) {
            return JobResult.unavailable();
        }
        int normalizedPage = Math.max(1, page);
        int normalizedSize = clampSize(size);

        if (!forceRefresh) {
            try {
                Page<JobDocument> indexed = jobRepository.findAll(
                        PageRequest.of(normalizedPage - 1, normalizedSize, Sort.by(Sort.Direction.ASC, "seq")));
                long total = indexed.getTotalElements();
                if (total > 0) {
                    List<YouthJob> items = indexed.getContent().stream().map(JobDocument::toItem).toList();
                    return new JobResult(items, (int) Math.min(total, Integer.MAX_VALUE),
                            (long) normalizedPage * normalizedSize < total, true);
                }
            } catch (RuntimeException exception) {
                log.debug("GG jobs ES read failed, falling back to live API: {}", exception.getMessage());
            }
        }

        GgJobsResult response = jobsClient.fetchJobs(normalizedPage, normalizedSize);
        if (!response.ok()) {
            return JobResult.unavailable();
        }
        boolean hasMore = (long) normalizedPage * normalizedSize < response.totalCount();
        return new JobResult(response.items(), response.totalCount(), hasMore, true);
    }

    public YouthResult policies(Region region, String category, String keyword, int page, int size, boolean forceRefresh) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = clampSize(size);
        String normalizedCategory = category == null ? "" : category.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String key = region.code() + "|" + normalizedCategory + "|" + normalizedKeyword
                + "|" + normalizedPage + "|" + normalizedSize;

        CacheEntry entry = cache.get(key);
        Instant now = Instant.now();
        if (!forceRefresh && entry != null && now.isBefore(entry.expiresAt())) {
            return entry.result();
        }

        YouthResult fresh = fetch(region, normalizedCategory, normalizedKeyword, normalizedPage, normalizedSize);
        if (fresh.items().isEmpty()) {
            // 빈 결과는 캐시하지 않는다: 일시적 API 실패가 TTL 동안 목록을 빈 채로 고정하는 것을 방지.
            if (entry != null) {
                log.info("Serving stale youth cache for key={} (fresh fetch empty)", key);
                return entry.result();
            }
            return fresh;
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.entrySet().removeIf(existing -> !existing.getValue().expiresAt().isAfter(now));
            if (cache.size() >= MAX_CACHE_ENTRIES) {
                cache.clear();
            }
        }
        cache.put(key, new CacheEntry(fresh, now.plus(Duration.ofSeconds(ttlSeconds))));
        return fresh;
    }

    private YouthResult fetch(Region region, String category, String keyword, int page, int size) {
        PortalPolicyResponse response = client.searchPolicies(region.ctpvNm(), keyword, page, size);
        if (response == null || response.searchResult() == null || response.searchResult().youthpolicy() == null) {
            return YouthResult.empty();
        }
        List<PortalPolicyResponse.Policy> raw = response.searchResult().youthpolicy();
        List<YouthPolicy> items = raw.stream()
                .map(policy -> toItem(policy, region))
                .filter(item -> item.id() != null && !item.id().isBlank())
                .toList();
        // 포털이 전체 건수(totalCount)를 주므로 다음 페이지 존재 여부를 정확히 계산.
        boolean hasMore = (long) page * size < response.totalCount();
        return new YouthResult(items, response.totalCount(), hasMore);
    }

    private static YouthPolicy toItem(PortalPolicyResponse.Policy policy, Region region) {
        String regionLabel = region == Region.ALL ? shortRegion(policy.regionNames()) : region.label();
        return new YouthPolicy(
                policy.docId(),
                NewsService.clean(policy.name()),
                NewsService.clean(policy.explanation()),
                NewsService.clean(policy.support()),
                splitKeywords(policy.keywords()),
                blankToNull(policy.largeClassification()),
                blankToNull(policy.mediumClassification()),
                regionLabel,
                blankToNull(policy.supervisorInstitution()),
                parseAge(policy.minAge()),
                parseAge(policy.maxAge()),
                blankToNull(policy.applyUrl()),
                blankToNull(policy.refUrl()),
                period(policy.bizBeginYmd(), policy.bizEndYmd(), null)
        );
    }

    /** STDG_NM("서울특별시 종로구,...")에서 첫 시도명만 뽑아 짧게. 없으면 "전국". */
    private static String shortRegion(String regionNames) {
        if (regionNames == null || regionNames.isBlank()) {
            return "전국";
        }
        String firstWord = regionNames.split(",")[0].trim().split(" ")[0];
        return firstWord.isBlank() ? "전국" : firstWord;
    }

    private static List<String> splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .toList();
    }

    private static Integer parseAge(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** yyyyMMdd~yyyyMMdd 사업기간을 우선 사용, 없으면 신청기간(aplyYmd) 원문. */
    private static String period(String begin, String end, String applyYmd) {
        String formattedBegin = formatYmd(begin);
        String formattedEnd = formatYmd(end);
        if (formattedBegin != null && formattedEnd != null) {
            return formattedBegin + " ~ " + formattedEnd;
        }
        if (formattedBegin != null) {
            return formattedBegin + " ~";
        }
        return blankToNull(applyYmd);
    }

    private static String formatYmd(String ymd) {
        if (ymd == null) {
            return null;
        }
        String digits = ymd.trim();
        if (digits.length() != 8 || !digits.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return digits.substring(0, 4) + "." + digits.substring(4, 6) + "." + digits.substring(6, 8);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    public record YouthResult(List<YouthPolicy> items, int totalCount, boolean hasMore) {
        static YouthResult empty() {
            return new YouthResult(List.of(), 0, false);
        }
    }

    private record CacheEntry(YouthResult result, Instant expiresAt) {
    }

    /** available=false면 미구성(키/서비스명 없음) 또는 업스트림 오류 — 프론트는 기존 뉴스로 degrade. */
    public record JobResult(List<YouthJob> items, int totalCount, boolean hasMore, boolean available) {
        static JobResult unavailable() {
            return new JobResult(List.of(), 0, false, false);
        }
    }
}
