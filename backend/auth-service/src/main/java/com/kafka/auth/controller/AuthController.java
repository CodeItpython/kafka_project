package com.kafka.auth.controller;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.dto.AuthDtos.EmailCodeRequest;
import com.kafka.auth.dto.AuthDtos.EmailCodeResponse;
import com.kafka.auth.dto.AuthDtos.EmailLoginRequest;
import com.kafka.auth.dto.AuthDtos.KakaoGuideResponse;
import com.kafka.auth.dto.AuthDtos.LoginRequest;
import com.kafka.auth.dto.AuthDtos.RegisterRequest;
import com.kafka.auth.dto.AuthDtos.UserResponse;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private final AuthService authService;
    private final String kakaoRedirectUri;
    private final String frontendRedirectUri;

    public AuthController(
            AuthService authService,
            @Value("${app.kakao.redirect-uri}") String kakaoRedirectUri,
            @Value("${app.frontend.redirect-uri}") String frontendRedirectUri
    ) {
        this.authService = authService;
        this.kakaoRedirectUri = kakaoRedirectUri;
        this.frontendRedirectUri = frontendRedirectUri;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/email/code")
    public ResponseEntity<EmailCodeResponse> createEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        return ResponseEntity.ok(authService.createEmailCode(request.email()));
    }

    @PostMapping("/email/login")
    public ResponseEntity<AuthResponse> loginWithEmailCode(@Valid @RequestBody EmailLoginRequest request) {
        return ResponseEntity.ok(authService.loginWithEmailCode(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(authService.toUserResponse(user));
    }

    @GetMapping("/oauth/kakao/guide")
    public ResponseEntity<KakaoGuideResponse> kakaoGuide() {
        return ResponseEntity.ok(new KakaoGuideResponse(
                "https://developers.kakao.com/",
                kakaoRedirectUri,
                new String[]{
                        "Create Kakao Developers application",
                        "Enable Kakao Login",
                        "Add " + frontendRedirectUri + " as Web platform domain",
                        "Add " + kakaoRedirectUri + " as redirect URI",
                        "Set KAKAO_CLIENT_ID and KAKAO_CLIENT_SECRET environment variables"
                }
        ));
    }
}
