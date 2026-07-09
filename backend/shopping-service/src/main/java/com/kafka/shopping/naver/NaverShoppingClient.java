package com.kafka.shopping.naver;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;

/**
 * Thin server-side proxy to Naver's shopping search API. The Client ID/Secret are
 * injected from the environment and only ever leave the server as request headers —
 * they are never exposed to the browser (that is the whole reason this MSA exists).
 */
@Component
@Slf4j
public class NaverShoppingClient {
    private final RestClient restClient;
    private final boolean configured;

    public NaverShoppingClient(
            RestClient.Builder builder,
            @Value("${app.naver.base-url:https://openapi.naver.com}") String baseUrl,
            @Value("${app.naver.client-id:}") String clientId,
            @Value("${app.naver.client-secret:}") String clientSecret
    ) {
        this.configured = clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        // 타임아웃을 둬 Naver가 느리거나 응답 없을 때 요청 스레드/배치 잡이 무한 대기하지 않게 한다.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-Naver-Client-Id", clientId == null ? "" : clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret == null ? "" : clientSecret)
                .build();
        if (!configured) {
            log.warn("Naver API credentials are not set (NAVER_CLIENT_ID / NAVER_CLIENT_SECRET). "
                    + "Product feed/search will report 503 until configured.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * @param sort one of sim(정확도), date(날짜), asc(가격오름차순), dsc(가격내림차순)
     */
    public NaverSearchResponse search(String query, int display, int start, String sort) {
        if (!configured) {
            throw new NaverNotConfiguredException("네이버 API 자격증명이 설정되지 않았습니다. 서버 환경변수 NAVER_CLIENT_ID / NAVER_CLIENT_SECRET를 설정하세요.");
        }
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/search/shop.json")
                        .queryParam("query", query)
                        .queryParam("display", display)
                        .queryParam("start", start)
                        .queryParam("sort", sort)
                        .build())
                .retrieve()
                .body(NaverSearchResponse.class);
    }
}
