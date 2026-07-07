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
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
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
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription
    ) {
        if (error != null && !error.isBlank()) {
            return redirectToFrontend("provider error: " + (errorDescription == null ? error : errorDescription));
        }
        if (code == null || code.isBlank()) {
            return redirectToFrontend("missing authorization code");
        }

        try {
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
        } catch (RuntimeException exception) {
            // 흔한 케이스: 브라우저가 콜백을 중복 호출 → 두 번째 요청의 코드는 이미 사용돼 토큰 교환 실패.
            // 첫 요청이 이미 로그인을 성공시켰으므로 에러 화면 대신 앱으로 돌려보낸다.
            log.warn("Kakao login token/userinfo exchange failed", exception);
            return redirectToFrontend("token/userinfo exchange failed: " + exception.getMessage());
        }
    }

    /** On any callback failure, bounce the browser back to the app instead of showing an error page. */
    private ResponseEntity<Void> redirectToFrontend(String reason) {
        log.warn("Kakao login callback did not complete ({}). Redirecting to {}.", reason, frontendRedirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, frontendRedirectUri)
                .build();
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
