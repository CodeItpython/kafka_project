package com.kafka.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.kafka.auth.dto.AuthDtos.AuthResponse;
import com.kafka.auth.dto.AuthDtos.EmailCodeResponse;
import com.kafka.auth.dto.AuthDtos.EmailLoginRequest;
import com.kafka.auth.email.EmailVerificationMailService;
import com.kafka.auth.email.EmailVerificationProperties;
import com.kafka.auth.model.AuthProvider;
import com.kafka.auth.model.EmailVerificationCode;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.EmailVerificationCodeRepository;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.security.JwtService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailVerificationMailService emailVerificationMailService;

    private final EmailVerificationProperties emailVerificationProperties = new EmailVerificationProperties();

    @Test
    void createEmailCodeSendsFourDigitCodeAndLoginVerifiesHashedCode() {
        AuthService service = new AuthService(
                userAccountRepository,
                emailVerificationCodeRepository,
                passwordEncoder,
                jwtService,
                emailVerificationMailService,
                emailVerificationProperties
        );
        ArgumentCaptor<EmailVerificationCode> verificationCodeCaptor = ArgumentCaptor.forClass(EmailVerificationCode.class);
        ArgumentCaptor<String> plainCodeCaptor = ArgumentCaptor.forClass(String.class);
        when(emailVerificationCodeRepository.save(any(EmailVerificationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EmailCodeResponse response = service.createEmailCode("USER@Example.COM");

        verify(emailVerificationCodeRepository).markUnusedCodesAsUsed("user@example.com");
        verify(emailVerificationCodeRepository).save(verificationCodeCaptor.capture());
        verify(emailVerificationMailService).sendVerificationCode(eq("user@example.com"), plainCodeCaptor.capture(), any(Instant.class));
        String plainCode = plainCodeCaptor.getValue();
        EmailVerificationCode savedCode = verificationCodeCaptor.getValue();
        assertThat(plainCode).matches("\\d{4}");
        assertThat(savedCode.getEmail()).isEqualTo("user@example.com");
        assertThat(savedCode.getCode()).isNotEqualTo(plainCode);
        assertThat(savedCode.getCode()).hasSize(64);
        assertThat(response.sentTo()).isEqualTo("us**@example.com");

        when(emailVerificationCodeRepository.findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(eq("user@example.com"), anyString()))
                .thenAnswer(invocation -> savedCode.getCode().equals(invocation.getArgument(1)) ? Optional.of(savedCode) : Optional.empty());
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createToken(any(UserAccount.class))).thenReturn("email-token");

        AuthResponse authResponse = service.loginWithEmailCode(new EmailLoginRequest("USER@Example.COM", plainCode, "메일유저"));

        assertThat(savedCode.isUsed()).isTrue();
        assertThat(authResponse.accessToken()).isEqualTo("email-token");
        assertThat(authResponse.user().email()).isEqualTo("user@example.com");
        assertThat(authResponse.user().provider()).isEqualTo(AuthProvider.EMAIL.name());
    }
}
