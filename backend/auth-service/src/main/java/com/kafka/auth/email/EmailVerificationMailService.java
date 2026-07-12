package com.kafka.auth.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject(properties.getSubject());
            // 평문(대체) + HTML 본문을 함께 담아 HTML 미지원 클라이언트도 코드를 볼 수 있게 한다.
            helper.setText(messageBody(code, expiresAt), htmlBody(code, expiresAt));
            mailSender.send(message);
            log.info("Email verification code sent. email={} expiresAt={}", email, expiresAt);
        } catch (MessagingException | MailException exception) {
            log.warn("Email verification code send failed. email={}", email, exception);
            throw new IllegalStateException("인증코드 이메일 발송에 실패했습니다.", exception);
        }
    }

    /**
     * 비밀번호 재설정 링크를 발송한다. SMTP 미설정(로컬/개발) 환경에서는 예외 대신
     * 링크를 로그로 남겨 흐름이 끊기지 않도록 우아하게 폴백한다(계정 열거 방지도 겸함).
     */
    public void sendPasswordResetLink(String email, String link, Instant expiresAt) {
        if (mailProperties.getHost() == null || mailProperties.getHost().isBlank()) {
            log.info("[password-reset] SMTP 미설정 — 재설정 링크를 로그로 대체합니다. email={} link={} expiresAt={}",
                    email, link, expiresAt);
            return;
        }
        String from = resolveFromAddress();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("[%s] 비밀번호 재설정 링크".formatted(properties.getAppName()));
            helper.setText(passwordResetBody(link, expiresAt));
            mailSender.send(message);
            log.info("Password reset link sent. email={} expiresAt={}", email, expiresAt);
        } catch (MessagingException | MailException exception) {
            log.warn("Password reset link send failed; link logged instead. email={} link={}", email, link, exception);
        }
    }

    private String passwordResetBody(String link, Instant expiresAt) {
        String formattedExpiresAt = EXPIRES_AT_FORMATTER.format(expiresAt.atZone(SEOUL_ZONE));
        return """
                %s 비밀번호 재설정 안내입니다.

                아래 링크에서 새 비밀번호를 설정해 주세요.
                %s

                이 링크는 %s까지 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(properties.getAppName(), link, formattedExpiresAt);
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

    /**
     * 앱 테마(인디고/라벤더)에 맞춘 HTML 본문. 이메일 클라이언트 호환을 위해
     * 테이블 레이아웃 + 인라인 스타일만 사용한다. String.format을 쓰지 않고
     * {{...}} 치환 방식이라 width="100%" 같은 리터럴 %를 이스케이프할 필요가 없다.
     */
    private String htmlBody(String code, Instant expiresAt) {
        String formattedExpiresAt = EXPIRES_AT_FORMATTER.format(expiresAt.atZone(SEOUL_ZONE));
        return HTML_TEMPLATE
                .replace("{{APP}}", escapeHtml(properties.getAppName()))
                .replace("{{CODE}}", escapeHtml(code))
                .replace("{{EXPIRES}}", escapeHtml(formattedExpiresAt));
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="ko">
            <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0; padding:0; background:#eef0f7; font-family:-apple-system,BlinkMacSystemFont,'Malgun Gothic','Apple SD Gothic Neo',sans-serif;">
              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#eef0f7; padding:32px 16px;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px; width:100%; background:#ffffff; border-radius:20px; overflow:hidden; box-shadow:0 12px 30px rgba(70,80,140,0.12);">
                      <tr>
                        <td style="background:#5b53eb; padding:26px 32px;">
                          <div style="color:#ffffff; font-size:13px; font-weight:800; letter-spacing:3px;">{{APP}}</div>
                          <div style="color:#ffffff; font-size:22px; font-weight:900; margin-top:6px;">이메일 인증</div>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:30px 32px 6px;">
                          <p style="margin:0; color:#4e5968; font-size:15px; line-height:1.6;">아래 인증코드를 입력해 이메일 인증을 완료해 주세요.</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:12px 32px 6px;">
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                            <tr>
                              <td align="center" style="background:#f2f0ff; border:1px solid #e0ddfb; border-radius:14px; padding:22px;">
                                <div style="color:#8b8ff0; font-size:12px; font-weight:800; letter-spacing:1px;">인증코드</div>
                                <div style="color:#5b53eb; font-size:38px; font-weight:900; letter-spacing:10px; margin-top:8px;">{{CODE}}</div>
                              </td>
                            </tr>
                          </table>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:16px 32px 4px;">
                          <p style="margin:0; color:#6b7280; font-size:13px; line-height:1.6;">이 코드는 <b style="color:#4e5968;">{{EXPIRES}}</b> 까지 유효합니다.</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:2px 32px 28px;">
                          <p style="margin:0; color:#9aa0c2; font-size:12px; line-height:1.6;">본인이 요청하지 않았다면 이 메일을 무시해 주세요.</p>
                        </td>
                      </tr>
                      <tr>
                        <td style="border-top:1px solid #eef0f7; background:#fafbff; padding:18px 32px;">
                          <div style="color:#9aa0c2; font-size:12px;">{{APP}} · 이 메일은 발신 전용입니다.</div>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """;
}
