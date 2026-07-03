package com.kafka.auth.email;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationMailService {
    private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailVerificationProperties properties;

    public void sendVerificationCode(String email, String code, Instant expiresAt) {
        validateSmtpHost();
        String from = resolveFromAddress();
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(properties.getSubject());
        message.setText(messageBody(code, expiresAt));
        try {
            mailSender.send(message);
            log.info("Email verification code sent. email={} expiresAt={}", email, expiresAt);
        } catch (MailException exception) {
            log.warn("Email verification code send failed. email={}", email, exception);
            throw new IllegalStateException("인증코드 이메일 발송에 실패했습니다.", exception);
        }
    }

    private void validateSmtpHost() {
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            throw new IllegalStateException("이메일 발송 설정이 완료되지 않았습니다.");
        }
    }

    private String resolveFromAddress() {
        if (properties.getFrom() != null && !properties.getFrom().isBlank()) {
            return properties.getFrom().trim();
        }
        if (mailProperties.getUsername() != null && !mailProperties.getUsername().isBlank()) {
            return mailProperties.getUsername().trim();
        }
        throw new IllegalStateException("이메일 발송 설정이 완료되지 않았습니다.");
    }

    private String messageBody(String code, Instant expiresAt) {
        String formattedExpiresAt = EXPIRES_AT_FORMATTER.format(expiresAt.atZone(SEOUL_ZONE));
        return """
                %s 이메일 인증코드입니다.

                인증코드: %s

                이 코드는 %s까지 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(properties.getAppName(), code, formattedExpiresAt);
    }
}
