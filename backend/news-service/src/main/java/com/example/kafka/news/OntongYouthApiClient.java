package com.example.kafka.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 온통청년 청년정책 Open API(한국고용정보원, youthcenter.go.kr /go/ythip/getPlcy) 프록시.
 * 청년 '정책·혜택' 탭의 데이터 소스다(취업·소식은 네이버 뉴스 검색을 그대로 재사용).
 *
 * <p>네이버와 달리 인증키는 헤더가 아니라 쿼리 파라미터(apiKeyVal)로 전달한다. data.go.kr가
 * 인코딩/디코딩 두 키를 주는데, RestClient의 queryParam이 값을 한 번 인코딩하므로 <b>디코딩(Decoding)
 * 키</b>를 넣어야 이중 인코딩으로 인증이 깨지지 않는다.
 *
 * <p>키 미설정({@code app.youth.api-key} 공백) 시 {@code configured=false}가 되어 호출을 건너뛰고
 * null을 반환한다 — 서비스는 정상 부팅하고 정책 탭만 빈 목록으로 우아하게 degrade한다(네이버 미설정과 동일 계약).
 */
@Component
@Slf4j
public class OntongYouthApiClient {
    private final RestClient restClient;
    private final String apiKey;
    private final boolean configured;

    public OntongYouthApiClient(
            RestClient.Builder builder,
            @Value("${app.youth.base-url:https://www.youthcenter.go.kr}") String baseUrl,
            @Value("${app.youth.api-key:}") String apiKey
    ) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.configured = !this.apiKey.isBlank();
        // 정부 API가 느리거나 응답 없을 때 요청 스레드가 무한 대기하지 않게 타임아웃을 둔다(네이버 클라이언트와 동일).
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                // 일부 정부 API는 Accept 미지정 시 XML을 준다 — JSON을 명시적으로 요청.
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                // 온통청년은 비브라우저 요청(기본 UA)엔 실제 API 대신 홈페이지 HTML(SPA 셸)을 돌려준다(WAF성 동작).
                // 브라우저처럼 보이는 헤더(UA/Referer/X-Requested-With)를 보내야 API 응답(JSON/에러)에 도달한다 — curl 실측 확인.
                .defaultHeader(HttpHeaders.USER_AGENT,
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                                + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")
                .defaultHeader(HttpHeaders.REFERER, "https://www.youthcenter.go.kr/")
                .defaultHeader("X-Requested-With", "XMLHttpRequest")
                .build();
        if (!configured) {
            log.warn("Youth policy API key is not set (YOUTH_API_KEY). 청년 정책 탭은 키 설정 전까지 빈 목록입니다.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * 청년정책 목록 조회. 미설정/실패 시 null(호출부는 빈 결과로 처리).
     *
     * @param keyword 정책 키워드(plcyKywdNm), 공백이면 생략
     * @param lclsfNm 정책 대분류명(일자리/주거/교육/복지문화/참여권리), 공백이면 생략
     * @param zipCd   법정동코드(콤마구분), null이면 생략(지역 필터는 응답 후처리로 처리)
     */
    public YouthPolicyApiResponse fetchPolicies(int pageNum, int pageSize, String keyword, String lclsfNm, String zipCd) {
        if (!configured) {
            return null;
        }
        try {
            return restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/go/ythip/getPlcy")
                                .queryParam("apiKeyVal", apiKey)
                                .queryParam("rtnType", "json")
                                .queryParam("pageType", "1")
                                .queryParam("pageNum", pageNum)
                                .queryParam("pageSize", pageSize);
                        if (keyword != null && !keyword.isBlank()) {
                            uriBuilder.queryParam("plcyKywdNm", keyword.trim());
                        }
                        if (lclsfNm != null && !lclsfNm.isBlank()) {
                            uriBuilder.queryParam("lclsfNm", lclsfNm.trim());
                        }
                        if (zipCd != null && !zipCd.isBlank()) {
                            uriBuilder.queryParam("zipCd", zipCd.trim());
                        }
                        return uriBuilder.build();
                    })
                    .retrieve()
                    .body(YouthPolicyApiResponse.class);
        } catch (RuntimeException exception) {
            // 나쁜 키/쿼터초과 시 XML/HTML 에러 봉투가 오면 JSON 파싱이 실패한다 — 여기서 흡수하고 빈 결과로 degrade.
            log.warn("Youth policy fetch failed (pageNum={}, keyword={}): {}", pageNum, keyword, exception.getMessage());
            return null;
        }
    }

    /**
     * getPlcy JSON 응답. 실제 필드는 발급 키로 검증 후 조정할 수 있으나, ignoreUnknown으로 여분 필드는 무시한다.
     * 페이징(pagging)은 표시용일 뿐이며 hasMore는 서비스가 raw 페이지 크기로 계산한다.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record YouthPolicyApiResponse(String resultCode, String resultMessage, Result result) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Result(Pagging pagging, List<Policy> youthPolicyList) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Pagging(Integer totCount, Integer pageNum, Integer pageSize) {
        }

        /** 정책 1건. 연령은 빈 문자열("")로 올 수 있어 String으로 받고 서비스에서 Integer로 방어 파싱한다. */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Policy(
                String plcyNo,
                String plcyNm,
                String plcyExplnCn,
                String plcySprtCn,
                String plcyKywdNm,
                String lclsfNm,
                String mclsfNm,
                String sprtTrgtMinAge,
                String sprtTrgtMaxAge,
                String zipCd,
                String rgtrInstCdNm,
                String sprvsnInstCdNm,
                String aplyUrlAddr,
                String refUrlAddr1,
                String bizPrdBgngYmd,
                String bizPrdEndYmd,
                String aplyYmd
        ) {
        }
    }
}
