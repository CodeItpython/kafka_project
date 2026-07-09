package com.example.kafka.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 온통청년(youthcenter.go.kr) 청년정책 조회 클라이언트. 공개 OPEN API(getPlcy)는 서버-사이드에서
 * 홈페이지 HTML만 돌려주고 실제 API 서버(:8080)는 외부에서 막혀 있어, 웹사이트가 실제로 쓰는
 * 포털 검색 API를 그대로 사용한다(API 키 불필요):
 *
 * <ol>
 *   <li>{@code GET /youthPolicy/ythPlcyTotalSearch} → HTML에서 {@code <meta name="_csrf">} 토큰과
 *       세션 쿠키(Set-Cookie)를 얻는다.</li>
 *   <li>{@code POST /pubot/search/portalPolicySearch} (x-csrf-token + 쿠키 + user_id:guest) →
 *       {@code searchResult.youthpolicy[]} 정책 목록 JSON.</li>
 * </ol>
 *
 * 지역 필터는 {@code STDG_CTPV_NM}(시도명: 서울특별시/경기도)로 서버가 처리한다.
 * 비공식 내부 API라 사이트 변경 시 깨질 수 있으므로 실패는 조용히 빈 결과로 degrade한다.
 */
@Component
@Slf4j
public class OntongYouthApiClient {
    private static final String SEARCH_PAGE = "/youthPolicy/ythPlcyTotalSearch";
    private static final String SEARCH_API = "/pubot/search/portalPolicySearch";
    private static final Pattern CSRF_META = Pattern.compile("name=\"_csrf\"\\s+content=\"([^\"]+)\"");
    private static final String BROWSER_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    // 포털 검색이 기대하는 필드들(미사용 필터는 빈 문자열).
    private static final String[] EMPTY_FIELDS = {
            "PVSN_INST_GROUP_CD", "SPRT_TRGT_AGE", "EARN_MIN_AMT", "EARN_MAX_AMT", "QLFC_ACBG_NM",
            "MRG_STTS_CD", "MJR_CND_NM", "EMPM_STTS_NM", "STDG_NM", "SPCL_FLD_NM", "USER_LCLSF_NO",
            "PLCY_KYWD_SN", "APLY_PRD_BGNG_YMD", "APLY_PRD_END_YMD", "APLY_PRD_SE_CD", "ODTM_CD"
    };

    private final RestClient restClient;
    private final String baseUrl;

    public OntongYouthApiClient(
            RestClient.Builder builder,
            @Value("${app.youth.base-url:https://www.youthcenter.go.kr}") String baseUrl
    ) {
        this.baseUrl = baseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, BROWSER_UA)
                .build();
    }

    /** 포털 검색은 키가 필요 없으므로 항상 사용 가능. */
    public boolean isConfigured() {
        return true;
    }

    /**
     * 청년정책 검색. 실패/미도달 시 null(호출부는 빈 결과로 처리).
     *
     * @param ctpvNm 시도명(서울특별시/경기도), 전체면 빈 문자열
     */
    public PortalPolicyResponse searchPolicies(String ctpvNm, String keyword, int pageNum, int listCount) {
        try {
            ResponseEntity<String> page = restClient.get()
                    .uri(SEARCH_PAGE)
                    .accept(MediaType.TEXT_HTML)
                    .retrieve()
                    .toEntity(String.class);
            String html = page.getBody() == null ? "" : page.getBody();
            Matcher matcher = CSRF_META.matcher(html);
            if (!matcher.find()) {
                log.warn("Youth: _csrf meta token not found on search page (site layout changed?)");
                return null;
            }
            String csrfToken = matcher.group(1);
            List<String> setCookies = page.getHeaders().get(HttpHeaders.SET_COOKIE);
            String cookieHeader = setCookies == null ? "" : setCookies.stream()
                    .map(cookie -> cookie.split(";", 2)[0])
                    .collect(Collectors.joining("; "));

            return restClient.post()
                    .uri(SEARCH_API)
                    .header("x-csrf-token", csrfToken)
                    .header("user_id", "guest")
                    .header("x-requested-with", "XMLHttpRequest")
                    .header(HttpHeaders.COOKIE, cookieHeader)
                    .header(HttpHeaders.REFERER, baseUrl + SEARCH_PAGE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(buildBody(ctpvNm, keyword, pageNum, listCount))
                    .retrieve()
                    .body(PortalPolicyResponse.class);
        } catch (RuntimeException exception) {
            log.warn("Youth policy search failed (ctpv={}, keyword={}): {}", ctpvNm, keyword, exception.getMessage());
            return null;
        }
    }

    private Map<String, Object> buildBody(String ctpvNm, String keyword, int pageNum, int listCount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", keyword == null ? "" : keyword.trim());
        body.put("pageNum", pageNum);
        body.put("listCount", listCount);
        body.put("sortFields", "DATE/DESC");
        body.put("searchFields", "all");
        body.put("STDG_CTPV_NM", ctpvNm == null ? "" : ctpvNm);
        for (String field : EMPTY_FIELDS) {
            body.put(field, "");
        }
        return body;
    }

    /** 포털 검색 응답. 정책 필드는 UPPER_SNAKE_CASE. ignoreUnknown으로 여분 필드는 무시. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PortalPolicyResponse(int totalCount, SearchResult searchResult) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record SearchResult(List<Policy> youthpolicy) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Policy(
                @JsonProperty("DOCID") String docId,
                @JsonProperty("PLCY_NM") String name,
                @JsonProperty("PLCY_EXPLN_CN") String explanation,
                @JsonProperty("PLCY_SPRT_CN") String support,
                @JsonProperty("PLCY_KYWD_NM") String keywords,
                @JsonProperty("USER_LCLSF_NM") String largeClassification,
                @JsonProperty("USER_MCLSF_NM") String mediumClassification,
                @JsonProperty("STDG_NM") String regionNames,
                @JsonProperty("SPRVSN_INST_CD_NM") String supervisorInstitution,
                @JsonProperty("SPRT_TRGT_MIN_AGE") String minAge,
                @JsonProperty("SPRT_TRGT_MAX_AGE") String maxAge,
                @JsonProperty("APLY_URL_ADDR") String applyUrl,
                @JsonProperty("REF_URL_ADDR1") String refUrl,
                @JsonProperty("BIZ_PRD_BGNG_YMD") String bizBeginYmd,
                @JsonProperty("BIZ_PRD_END_YMD") String bizEndYmd
        ) {
        }
    }
}
