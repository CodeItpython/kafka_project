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

@Service
public class KakaoOAuthService {
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient restClient;
    private final AuthService authService;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOAuthService(
            AuthService authService,
            @Value("${app.kakao.client-id}") String clientId,
            @Value("${app.kakao.client-secret}") String clientSecret,
            @Value("${app.kakao.redirect-uri}") String redirectUri
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

    public String getClientId() {
        return clientId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public AuthResponse loginWithAuthorizationCode(String code) {
        KakaoTokenResponse token = requestToken(code);
        KakaoUserResponse kakaoUser = requestUser(token.accessToken());

        String kakaoId = String.valueOf(kakaoUser.id());
        String email = Optional.ofNullable(kakaoUser.kakaoAccount())
                .map(KakaoAccount::email)
                .orElse(null);
        String nickname = Optional.ofNullable(kakaoUser.kakaoAccount())
                .map(KakaoAccount::profile)
                .map(KakaoProfile::nickname)
                .or(() -> Optional.ofNullable(kakaoUser.properties()).map(KakaoProperties::nickname))
                .orElse("카카오 사용자");

        return authService.loginWithKakao(kakaoId, email, nickname);
    }

    private KakaoTokenResponse requestToken(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            body.add("client_secret", clientSecret);
        }

        return restClient.post()
                .uri(TOKEN_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    private KakaoUserResponse requestUser(String accessToken) {
        return restClient.get()
                .uri(USER_INFO_URL)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(KakaoUserResponse.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoTokenResponse(
            @JsonProperty("access_token") String accessToken
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount,
            KakaoProperties properties
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            KakaoProfile profile
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProfile(
            String nickname
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProperties(
            String nickname
    ) {
    }
}
