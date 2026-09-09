package com.kafka.shopping.naver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 쿠팡 파트너스 Open API 상품검색 클라이언트. 네이버 쇼핑 검색 API가 중단(SE05)되어
 * 카탈로그 소스를 대체하기 위한 것으로, 키가 설정된 경우에만 활성화된다.
 *
 * <p>인증은 쿠팡의 CEA HMAC 방식:
 * {@code Authorization: CEA algorithm=HmacSHA256, access-key=..., signed-date=..., signature=...}
 * 서명 대상 메시지는 {@code signedDate + METHOD + path + query}(쿼리는 '?' 제외)이며,
 * signed-date 는 GMT 기준 {@code yyMMdd'T'HHmmss'Z'} 포맷이다.</p>
 *
 * <p>키는 파트너스 콘솔에서 발급받아 env 로만 주입한다(깃 노출 금지):
 * {@code COUPANG_ACCESS_KEY}, {@code COUPANG_SECRET_KEY}.</p>
 */
@Component
@Slf4j
public class CoupangProductClient {
    private static final String SEARCH_PATH =
            "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";
    private static final DateTimeFormatter SIGNED_DATE =
            DateTimeFormatter.ofPattern("yyMMdd'T'HHmmss'Z'", Locale.US);
    /** 쿠팡 상품검색은 offset 페이징이 없다(limit 만 지원) — 2페이지 이상은 빈 결과. */
    private static final int MAX_LIMIT = 100;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String accessKey;
    private final String secretKey;

    public CoupangProductClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${app.shopping.coupang.base-url:https://api-gateway.coupang.com}") String baseUrl,
            @Value("${app.shopping.coupang.access-key:}") String accessKey,
            @Value("${app.shopping.coupang.secret-key:}") String secretKey
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.accessKey = accessKey == null ? "" : accessKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = builder.requestFactory(requestFactory).build();
    }

    /** 액세스키·시크릿키가 모두 있어야 카탈로그 소스로 쓴다. */
    public boolean isConfigured() {
        return !accessKey.isBlank() && !secretKey.isBlank();
    }

    /**
     * 키워드로 상품을 검색한다. 실패하면 빈 목록(호출부가 네이버로 폴백하거나 색인을 유지).
     * offset 페이징이 없어 start > 1 이면 빈 목록을 돌려준다.
     */
    public List<ProductResponse> search(String keyword, int limit, int start) {
        if (!isConfigured() || keyword == null || keyword.isBlank() || start > 1) {
            return List.of();
        }
        int size = Math.max(1, Math.min(limit, MAX_LIMIT));
        String query = "keyword=" + urlEncode(keyword) + "&limit=" + size;
        try {
            String body = restClient.get()
                    .uri(URI.create(baseUrl + SEARCH_PATH + "?" + query))
                    .header(HttpHeaders.AUTHORIZATION, authorization("GET", SEARCH_PATH, query))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (RuntimeException exception) {
            log.warn("Coupang product search failed (keyword={}): {}", keyword, exception.getMessage());
            return List.of();
        }
    }

    private List<ProductResponse> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String code = root.path("rCode").asText("");
            if (!code.isBlank() && !"0".equals(code)) {
                log.warn("Coupang API non-OK: rCode={} rMessage={}", code, root.path("rMessage").asText(""));
                return List.of();
            }
            JsonNode products = root.path("data").path("productData");
            if (!products.isArray()) {
                return List.of();
            }
            List<ProductResponse> items = new ArrayList<>(products.size());
            for (JsonNode product : products) {
                String id = product.path("productId").asText(null);
                String name = product.path("productName").asText(null);
                if (id == null || name == null || name.isBlank()) {
                    continue;
                }
                items.add(new ProductResponse(
                        id,
                        name,
                        product.path("productUrl").asText(null),
                        product.path("productImage").asText(null),
                        product.path("productPrice").asLong(0),
                        0L,
                        "쿠팡",
                        null,
                        product.path("categoryName").asText(null)));
            }
            return List.copyOf(items);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            log.warn("Coupang response parse failed: {}", exception.getMessage());
            return List.of();
        }
    }

    /** CEA HMAC 서명 헤더. 메시지는 signedDate + METHOD + path + query(‘?’ 제외). */
    private String authorization(String method, String path, String query) {
        String signedDate = ZonedDateTime.now(ZoneOffset.UTC).format(SIGNED_DATE);
        String message = signedDate + method + path + query;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                hex.append(String.format("%02x", b));
            }
            return "CEA algorithm=HmacSHA256, access-key=" + accessKey
                    + ", signed-date=" + signedDate + ", signature=" + hex;
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("쿠팡 서명 생성 실패", exception);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
