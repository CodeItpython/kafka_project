package com.kafka.auth.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.kafka.auth.dto.AuthDtos.AuthResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "네이버 아이디로 로그인" (OAuth 2.0 authorization code). Mirrors {@link KakaoOAuthService};
 * Naver additionally mandates a {@code state} value for CSRF protection, which the controller
 * generates, stores in a cookie, and echoes back here for the token exchange.
 */
@Service
public class NaverOAuthService {
    private static final String AUTHORIZE_URL = "https://nid.naver.com/oauth2.0/authorize";
    private static final String TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    private final RestClient restClient;
    private final AuthService authService;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public NaverOAuthService(
            AuthService authService,
            @Value("${app.naver-login.client-id}") String clientId,
            @Value("${app.naver-login.client-secret}") String clientSecret,
            @Value("${app.naver-login.redirect-uri}") String redirectUri
    ) {
        this.restClient = RestClient.create();
        this.authService = authService;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public String buildAuthorizeUri(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    public AuthResponse loginWithAuthorizationCode(String code, String state) {
        NaverTokenResponse token = requestToken(code, state);
        NaverUserResponse user = requestUser(token.accessToken());
        NaverUserResponse.Profile profile = user == null ? null : user.response();
        if (profile == null || profile.id() == null || profile.id().isBlank()) {
            throw new IllegalStateException("네이버 사용자 정보를 가져오지 못했습니다.");
        }
        String name = Optional.ofNullable(profile.name())
                .filter(value -> !value.isBlank())
                .or(() -> Optional.ofNullable(profile.nickname()).filter(value -> !value.isBlank()))
                .orElse("네이버 사용자");
        return authService.loginWithNaver(profile.id(), profile.email(), name);
    }

    private NaverTokenResponse requestToken(String code, String state) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("state", state);

        return restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(NaverTokenResponse.class);
    }

    private NaverUserResponse requestUser(String accessToken) {
        return restClient.get()
                .uri(USER_INFO_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(NaverUserResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NaverUserResponse(
            String resultcode,
            String message,
            Profile response
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Profile(String id, String email, String name, String nickname) {
        }
    }
}
