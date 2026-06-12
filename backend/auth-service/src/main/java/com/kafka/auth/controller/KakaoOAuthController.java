package com.kafka.auth.controller;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.service.KakaoOAuthService;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
public class KakaoOAuthController {
    private final KakaoOAuthService kakaoOAuthService;
    private final ErrorPageTemplate errorPageTemplate;
    private final String frontendRedirectUri;

    public KakaoOAuthController(
            KakaoOAuthService kakaoOAuthService,
            ErrorPageTemplate errorPageTemplate,
            @Value("${app.frontend.redirect-uri}") String frontendRedirectUri
    ) {
        this.kakaoOAuthService = kakaoOAuthService;
        this.errorPageTemplate = errorPageTemplate;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @GetMapping("/api/auth/oauth/kakao/authorize")
    public ResponseEntity<?> authorize() {
        if (!kakaoOAuthService.isConfigured()) {
            return errorPageTemplate.badRequest(
                    "카카오 로그인 설정 필요",
                    "KAKAO_CLIENT_ID가 설정되어 있지 않습니다. Kakao Developers의 REST API 키를 백엔드 실행 환경변수에 등록해주세요.",
                    frontendRedirectUri
            );
        }

        URI location = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code")
                .queryParam("client_id", kakaoOAuthService.getClientId())
                .queryParam("redirect_uri", kakaoOAuthService.getRedirectUri())
                .build(true)
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location.toString())
                .build();
    }

    @GetMapping("/oauth2/callback/kakao")
    public ResponseEntity<String> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription
    ) {
        if (error != null && !error.isBlank()) {
            return errorPageTemplate.badRequest("카카오 로그인 실패", "카카오 인증이 취소되었거나 실패했습니다: " + errorDescription, frontendRedirectUri);
        }
        if (code == null || code.isBlank()) {
            return errorPageTemplate.badRequest("카카오 로그인 실패", "카카오 인증 정보를 받지 못했습니다.", frontendRedirectUri);
        }

        AuthResponse authResponse = kakaoOAuthService.loginWithAuthorizationCode(code);
        String redirectTarget = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                .fragment("access_token=" + authResponse.accessToken())
                .build()
                .toUriString();
        String body = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="UTF-8"><title>카카오 로그인</title></head>
                <body>
                <script>
                  location.replace(%s);
                </script>
                </body>
                </html>
                """.formatted(jsString(redirectTarget));
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(body);
    }

    private String jsString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

}
