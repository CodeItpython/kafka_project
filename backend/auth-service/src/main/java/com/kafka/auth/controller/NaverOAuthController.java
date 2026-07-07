package com.kafka.auth.controller;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.service.NaverOAuthService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class NaverOAuthController {
    private static final String STATE_COOKIE = "naver_oauth_state";

    private final NaverOAuthService naverOAuthService;
    private final ErrorPageTemplate errorPageTemplate;
    private final String frontendRedirectUri;

    public NaverOAuthController(
            NaverOAuthService naverOAuthService,
            ErrorPageTemplate errorPageTemplate,
            @Value("${app.frontend.redirect-uri}") String frontendRedirectUri
    ) {
        this.naverOAuthService = naverOAuthService;
        this.errorPageTemplate = errorPageTemplate;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @GetMapping("/api/auth/oauth/naver/authorize")
    public ResponseEntity<?> authorize() {
        if (!naverOAuthService.isConfigured()) {
            return errorPageTemplate.badRequest(
                    "네이버 로그인 설정 필요",
                    "NAVER_LOGIN_CLIENT_ID가 설정되어 있지 않습니다. 네이버 개발자센터에서 '네이버 로그인' 애플리케이션을 등록하고 Client ID/Secret을 백엔드 실행 환경변수에 등록해주세요.",
                    frontendRedirectUri
            );
        }

        String state = UUID.randomUUID().toString().replace("-", "");
        String location = naverOAuthService.buildAuthorizeUri(state);
        ResponseCookie stateCookie = ResponseCookie.from(STATE_COOKIE, state)
                .httpOnly(true)
                .path("/")
                .maxAge(300)
                .sameSite("Lax")
                .build();
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    @GetMapping("/oauth2/callback/naver")
    public ResponseEntity<?> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false, name = "error_description") String errorDescription,
            @CookieValue(name = STATE_COOKIE, required = false) String expectedState
    ) {
        if (error != null && !error.isBlank()) {
            return redirectToFrontend("provider error: " + (errorDescription == null ? error : errorDescription));
        }
        if (code == null || code.isBlank()) {
            return redirectToFrontend("missing authorization code");
        }
        if (expectedState == null || state == null || !expectedState.equals(state)) {
            // 흔한 케이스: 브라우저가 콜백을 중복 호출해 두 번째 요청이 도착. 이미 성공한 첫 요청이
            // 1회용 state 쿠키를 지운 뒤라 여기로 온다. 에러 화면 대신 앱으로 돌려보낸다(첫 요청이
            // 이미 토큰을 프론트에 전달했으므로 사용자는 로그인된 상태로 끝난다).
            return redirectToFrontend("state validation failed (likely a duplicate or expired callback)");
        }

        try {
            AuthResponse authResponse = naverOAuthService.loginWithAuthorizationCode(code, state);
            String redirectTarget = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                    .fragment("access_token=" + authResponse.accessToken())
                    .build()
                    .toUriString();
            String body = """
                    <!doctype html>
                    <html lang="ko">
                    <head><meta charset="UTF-8"><title>네이버 로그인</title></head>
                    <body>
                    <script>
                      location.replace(%s);
                    </script>
                    </body>
                    </html>
                    """.formatted(jsString(redirectTarget));
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, clearStateCookie().toString())
                    .contentType(MediaType.TEXT_HTML)
                    .body(body);
        } catch (RuntimeException exception) {
            log.warn("Naver login token/userinfo exchange failed", exception);
            return redirectToFrontend("token/userinfo exchange failed: " + exception.getMessage());
        }
    }

    /** On any callback failure, bounce the browser back to the app instead of showing an error page. */
    private ResponseEntity<Void> redirectToFrontend(String reason) {
        log.warn("Naver login callback did not complete ({}). Redirecting to {}.", reason, frontendRedirectUri);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, clearStateCookie().toString())
                .header(HttpHeaders.LOCATION, frontendRedirectUri)
                .build();
    }

    private ResponseCookie clearStateCookie() {
        return ResponseCookie.from(STATE_COOKIE, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
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
