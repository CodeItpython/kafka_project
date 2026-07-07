package com.example.kafka.news;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 네이버 뉴스 검색 API(openapi.naver.com/v1/search/news.json) 프록시.
 * "검색" 오픈 API 하나가 뉴스·쇼핑·블로그를 모두 커버하므로 쇼핑과 동일한 Client ID/Secret을 재사용한다.
 * start/display 페이지네이션을 지원해 무한 스크롤이 가능하다(크롤링과 달리).
 */
@Component
@Slf4j
public class NaverNewsApiClient {
    private final RestClient restClient;
    private final boolean configured;

    public NaverNewsApiClient(
            RestClient.Builder builder,
            @Value("${app.naver.base-url:https://openapi.naver.com}") String baseUrl,
            @Value("${app.naver.client-id:}") String clientId,
            @Value("${app.naver.client-secret:}") String clientSecret
    ) {
        this.configured = clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        this.restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader("X-Naver-Client-Id", clientId == null ? "" : clientId)
                .defaultHeader("X-Naver-Client-Secret", clientSecret == null ? "" : clientSecret)
                .build();
        if (!configured) {
            log.warn("Naver API credentials are not set (NAVER_CLIENT_ID / NAVER_CLIENT_SECRET). News feed will be empty until configured.");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * @param sort sim(정확도) 또는 date(날짜순)
     * @return 응답(미설정/실패 시 null)
     */
    public NaverNewsResponse search(String query, int display, int start, String sort) {
        if (!configured) {
            return null;
        }
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/search/news.json")
                            .queryParam("query", query)
                            .queryParam("display", display)
                            .queryParam("start", start)
                            .queryParam("sort", sort)
                            .build())
                    .retrieve()
                    .body(NaverNewsResponse.class);
        } catch (RuntimeException exception) {
            log.warn("Naver news search failed (query={}, start={}): {}", query, start, exception.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NaverNewsResponse(int total, int start, int display, List<Item> items) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Item(
                String title,
                String link,
                @JsonProperty("originallink") String originalLink,
                String description,
                @JsonProperty("pubDate") String pubDate
        ) {
        }
    }
}
