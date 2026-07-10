package com.example.kafka.news;

import com.example.kafka.news.YouthDtos.YouthJob;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 경기데이터드림(openapi.gg.go.kr) '경기도 내 공공일자리 정보'(원본: 경기도일자리재단 잡아바, www.jobaba.net) 클라이언트.
 *
 * <p>경기데이터드림 표준 OpenAPI 규격:
 * {@code GET {base}/{serviceName}?KEY={key}&Type=json&pIndex={n}&pSize={n}} →
 * {@code { "<ServiceName>": [ {"head":[{"list_total_count":N},{"RESULT":{"CODE","MESSAGE"}}]}, {"row":[{...}]} ] } }.
 * 인증키/서비스명은 data.gg.go.kr에서 발급/확인해 env로만 주입한다(깃 노출 금지):
 * {@code GG_JOBS_API_KEY}, {@code GG_JOBS_SERVICE_NAME}. 둘 중 하나라도 없으면 미구성으로 보고 조용히 빈 결과를 낸다.</p>
 *
 * <p>행(row)의 컬럼명은 데이터셋마다 달라, 5개 표시 필드(공고명/기관명/모집시작/모집종료/상세URL)를
 * <b>설정값 우선 + 휴리스틱 폴백</b>으로 해석한다. 설정값({@code app.youth.jobs.field.*})을 주면 그대로 쓰고,
 * 비었으면 컬럼명 패턴으로 추정한다(대개 무설정으로 동작). 활성화 후 매핑이 어긋나면 field.* 만 지정하면 된다.</p>
 */
@Component
@Slf4j
public class GgJobsApiClient {
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final String OK_CODE = "INFO-000";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceName;
    private final String apiKey;
    private final FieldOverride fieldOverride;

    public GgJobsApiClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.youth.jobs.base-url:https://openapi.gg.go.kr}") String baseUrl,
            @Value("${app.youth.jobs.service-name:}") String serviceName,
            @Value("${app.youth.jobs.api-key:}") String apiKey,
            @Value("${app.youth.jobs.field.title:}") String fieldTitle,
            @Value("${app.youth.jobs.field.org:}") String fieldOrg,
            @Value("${app.youth.jobs.field.start:}") String fieldStart,
            @Value("${app.youth.jobs.field.end:}") String fieldEnd,
            @Value("${app.youth.jobs.field.url:}") String fieldUrl
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.serviceName = serviceName == null ? "" : serviceName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.fieldOverride = new FieldOverride(
                blankToNull(fieldTitle), blankToNull(fieldOrg),
                blankToNull(fieldStart), blankToNull(fieldEnd), blankToNull(fieldUrl));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("User-Agent", BROWSER_UA)
                .build();
    }

    /** 인증키와 서비스명이 모두 설정됐는지(프론트가 "미설정" vs "결과 없음"을 구분하도록 노출). */
    public boolean isConfigured() {
        return !apiKey.isBlank() && !serviceName.isBlank();
    }

    /** 채용공고 조회. 미구성/실패 시 조용히 빈 결과(configured 플래그로 구분). */
    public GgJobsResult fetchJobs(int pIndex, int pSize) {
        if (!isConfigured()) {
            return GgJobsResult.unconfigured();
        }
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/{svc}")
                    .queryParam("KEY", apiKey)
                    .queryParam("Type", "json")
                    .queryParam("pIndex", pIndex)
                    .queryParam("pSize", pSize)
                    .build(serviceName);
            String body = restClient.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN, MediaType.TEXT_HTML)
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RuntimeException exception) {
            log.warn("GG jobs fetch failed (service={}, pIndex={}): {}", serviceName, pIndex, exception.getMessage());
            return GgJobsResult.error(exception.getMessage());
        }
    }

    private GgJobsResult parse(String body) {
        if (body == null || body.isBlank()) {
            return GgJobsResult.error("empty body");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn("GG jobs: JSON parse failed: {}", head(body));
            return GgJobsResult.error("parse failed");
        }
        // 최상위 RESULT만 있으면 오류 응답(잘못된 KEY/서비스명 등).
        JsonNode topResult = root.get("RESULT");
        if (topResult != null && root.size() <= 1) {
            String code = text(topResult, "CODE");
            String message = text(topResult, "MESSAGE");
            log.warn("GG jobs API error: code={} message={}", code, message);
            return GgJobsResult.error(code + " " + message);
        }
        // 서비스명 래퍼(배열) 탐색: {"<ServiceName>":[{head},{row}]}.
        JsonNode wrapper = firstArray(root);
        if (wrapper == null) {
            log.warn("GG jobs: unexpected response shape: {}", head(body));
            return GgJobsResult.error("unexpected shape");
        }
        int totalCount = 0;
        JsonNode rows = null;
        for (JsonNode part : wrapper) {
            JsonNode head = part.get("head");
            if (head != null && head.isArray()) {
                for (JsonNode h : head) {
                    if (h.has("list_total_count")) {
                        totalCount = h.get("list_total_count").asInt(0);
                    }
                    JsonNode result = h.get("RESULT");
                    if (result != null) {
                        String code = text(result, "CODE");
                        if (code != null && !code.isBlank() && !OK_CODE.equals(code)) {
                            log.warn("GG jobs API non-OK: code={} message={}", code, text(result, "MESSAGE"));
                            return GgJobsResult.error(code);
                        }
                    }
                }
            }
            JsonNode row = part.get("row");
            if (row != null && row.isArray()) {
                rows = row;
            }
        }
        if (rows == null || rows.isEmpty()) {
            return new GgJobsResult(totalCount, List.of(), true, true, null);
        }
        FieldMap fields = resolveFields(rows.get(0));
        List<YouthJob> items = new ArrayList<>(rows.size());
        for (JsonNode row : rows) {
            YouthJob job = toJob(row, fields);
            if (job != null) {
                items.add(job);
            }
        }
        return new GgJobsResult(totalCount, items, true, true, null);
    }

    private YouthJob toJob(JsonNode row, FieldMap fields) {
        String title = clean(pick(row, fields.title()));
        if (title == null || title.isBlank()) {
            return null; // 제목 없는 행은 표시 가치 없음
        }
        String org = clean(pick(row, fields.org()));
        String start = formatYmd(pick(row, fields.start()));
        String end = formatYmd(pick(row, fields.end()));
        String url = clean(pick(row, fields.url()));
        // 위치 독립적 안정 id: 목록이 밀려도 같은 공고는 같은 id → 프론트 무한스크롤 dedupe가 깨지지 않는다.
        // (JobDocument의 색인 id와 동일 규칙이라 ES/라이브 경로 간에도 같은 공고는 같은 id)
        String basis = (url == null ? "" : url) + "|" + title + "|" + (org == null ? "" : org)
                + "|" + (start == null ? "" : start);
        String id = UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString();
        return new YouthJob(id, title, org, start, end, period(start, end), url);
    }

    /** 설정 override 우선, 없으면 컬럼명 휴리스틱으로 5개 표시 필드를 해석. */
    private FieldMap resolveFields(JsonNode sampleRow) {
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = sampleRow.fieldNames(); it.hasNext(); ) {
            keys.add(it.next());
        }
        String title = orGuess(fieldOverride.title(), keys, this::guessTitle);
        String org = orGuess(fieldOverride.org(), keys, this::guessOrg);
        String start = orGuess(fieldOverride.start(), keys, this::guessStart);
        String end = orGuess(fieldOverride.end(), keys, this::guessEnd);
        String url = orGuess(fieldOverride.url(), keys, this::guessUrl);
        log.info("GG jobs field map: title={} org={} start={} end={} url={} (keys={})",
                title, org, start, end, url, keys);
        return new FieldMap(title, org, start, end, url);
    }

    private String orGuess(String override, List<String> keys, java.util.function.Function<List<String>, String> guesser) {
        if (override != null) {
            return override;
        }
        return guesser.apply(keys);
    }

    private String guessTitle(List<String> keys) {
        String hit = firstMatch(keys, k -> k.contains("TTL") || k.contains("TITLE") || k.endsWith("SJ") || k.contains("공고"));
        if (hit != null) {
            return hit;
        }
        // 이름 계열 중 기관명이 아닌 것.
        return firstMatch(keys, k -> k.contains("NM") && !isOrgKey(k) && !k.contains("SIGUN") && !k.contains("CTPV"));
    }

    private String guessOrg(List<String> keys) {
        return firstMatch(keys, this::isOrgKey);
    }

    private boolean isOrgKey(String upper) {
        return (upper.contains("INST") || upper.contains("ORGN") || upper.contains("ORG")
                || upper.contains("CMPNY") || upper.contains("COMPANY") || upper.contains("CORP")
                || upper.contains("기관")) && (upper.contains("NM") || upper.contains("기관"));
    }

    private String guessStart(List<String> keys) {
        return firstMatch(keys, k -> isDateKey(k)
                && (k.contains("BGNG") || k.contains("BEGIN") || k.contains("START") || k.contains("STR")
                    || k.contains("FROM") || k.contains("접수") || k.contains("시작")));
    }

    private String guessEnd(List<String> keys) {
        return firstMatch(keys, k -> isDateKey(k)
                && (k.contains("END") || k.contains("CLOS") || k.contains("TO") || k.contains("종료") || k.contains("마감")));
    }

    private boolean isDateKey(String upper) {
        return upper.contains("YMD") || upper.contains("DE") || upper.contains("DT")
                || upper.contains("DATE") || upper.contains("일자") || upper.contains("YSDT");
    }

    private String guessUrl(List<String> keys) {
        return firstMatch(keys, k -> k.contains("URL") || k.contains("ADDR") || k.contains("LINK")
                || k.contains("HMPG") || k.contains("HOMEPAGE"));
    }

    /** 원본 대소문자 키를 유지하되 비교는 대문자로. */
    private String firstMatch(List<String> keys, java.util.function.Predicate<String> matcher) {
        return keys.stream()
                .filter(k -> matcher.test(k.toUpperCase(java.util.Locale.ROOT)))
                .findFirst()
                .orElse(null);
    }

    private static String pick(JsonNode row, String key) {
        if (key == null) {
            return null;
        }
        JsonNode node = row.get(key);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static JsonNode firstArray(JsonNode root) {
        if (root == null || !root.isObject()) {
            return null;
        }
        for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
            JsonNode value = root.get(it.next());
            if (value != null && value.isArray() && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /** "YYYY-MM-DD"/"YYYYMMDD"/"YYYY.MM.DD" → "YYYY.MM.DD". 그 외는 원문(공백이면 null). */
    private static String formatYmd(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9]", "");
        // 8자리(YYYYMMDD) 또는 시각이 붙은 형태(YYYYMMDDHHMMSS 등)면 앞 8자리만 날짜로 포맷.
        if (digits.length() >= 8) {
            return digits.substring(0, 4) + "." + digits.substring(4, 6) + "." + digits.substring(6, 8);
        }
        return value;
    }

    private static String period(String start, String end) {
        if (start != null && end != null) {
            return start + " ~ " + end;
        }
        if (start != null) {
            return start + " ~";
        }
        if (end != null) {
            return "~ " + end;
        }
        return null;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.replaceAll("\\s+", " ").trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String head(String body) {
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    /** 조회 결과. configured=false면 키/서비스명 미설정, ok=false면 API 오류(둘 다 items는 빈 목록). */
    public record GgJobsResult(int totalCount, List<YouthJob> items, boolean configured, boolean ok, String message) {
        static GgJobsResult unconfigured() {
            return new GgJobsResult(0, List.of(), false, false, "not configured");
        }

        static GgJobsResult error(String message) {
            return new GgJobsResult(0, List.of(), true, false, message);
        }
    }

    private record FieldOverride(String title, String org, String start, String end, String url) {
    }

    private record FieldMap(String title, String org, String start, String end, String url) {
    }
}
