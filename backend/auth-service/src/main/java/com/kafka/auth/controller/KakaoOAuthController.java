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
    private final String frontendRedirectUri;

    public KakaoOAuthController(
            KakaoOAuthService kakaoOAuthService,
            @Value("${app.frontend.redirect-uri}") String frontendRedirectUri
    ) {
        this.kakaoOAuthService = kakaoOAuthService;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @GetMapping("/api/auth/oauth/kakao/authorize")
    public ResponseEntity<Void> authorize() {
        if (!kakaoOAuthService.isConfigured()) {
            throw new IllegalStateException("KAKAO_CLIENT_ID가 설정되어 있지 않습니다.");
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
            return html("카카오 로그인 실패", "카카오 인증이 취소되었거나 실패했습니다: " + errorDescription);
        }
        if (code == null || code.isBlank()) {
            return html("카카오 로그인 실패", "authorization code가 없습니다.");
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

    private ResponseEntity<String> html(String title, String message) {
        String body = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="UTF-8"><title>%s</title></head>
                <body><h1>%s</h1><p>%s</p><p><a href="%s">돌아가기</a></p></body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message), escapeHtml(frontendRedirectUri));
        return ResponseEntity.badRequest()
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

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
