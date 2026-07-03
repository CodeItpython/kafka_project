package com.kafka.auth.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;
import java.io.UnsupportedEncodingException;
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
    private static final DateTimeFormatter EXPIRES_AT_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm");
    private static final String CHARSET = "UTF-8";

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final EmailVerificationProperties properties;

    public void sendVerificationCode(String email, String code, Instant expiresAt) {
        validateSmtpHost();
        String from = resolveFromAddress();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, CHARSET);
            helper.setFrom(from);
            helper.setTo(email);
            message.setHeader("Subject", encodedSubject());
            helper.setText(plainTextBody(code, expiresAt), htmlBody(code, expiresAt));
            mailSender.send(message);
            log.info("Email verification code sent. email={} expiresAt={}", email, expiresAt);
        } catch (MessagingException | MailException | UnsupportedEncodingException exception) {
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

    private String encodedSubject() throws UnsupportedEncodingException {
        return MimeUtility.encodeText(properties.getSubject(), CHARSET, "B");
    }

    private String plainTextBody(String code, Instant expiresAt) {
        String formattedExpiresAt = EXPIRES_AT_FORMATTER.format(expiresAt.atZone(SEOUL_ZONE));
        return """
                %s 로그인 인증번호입니다.

                인증번호: %s

                이 코드는 %s까지 사용할 수 있습니다.
                본인이 요청하지 않았다면 이 메일을 무시해주세요.
                """.formatted(properties.getAppName(), code, formattedExpiresAt);
    }

    private String htmlBody(String code, Instant expiresAt) {
        String appName = escapeHtml(properties.getAppName());
        String escapedCode = escapeHtml(code);
        String formattedExpiresAt = escapeHtml(EXPIRES_AT_FORMATTER.format(expiresAt.atZone(SEOUL_ZONE)));
        return """
                <!doctype html>
                <html lang="ko">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s 인증번호</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f6f8;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans KR','Apple SD Gothic Neo',Arial,sans-serif;color:#191f28;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f6f8;margin:0;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#ffffff;border-radius:18px;overflow:hidden;border:1px solid #e5e8eb;">
                          <tr>
                            <td style="padding:28px 28px 18px 28px;">
                              <div style="font-size:14px;line-height:20px;color:#4e5968;font-weight:700;">%s</div>
                              <h1 style="margin:10px 0 0 0;font-size:24px;line-height:32px;color:#191f28;font-weight:800;letter-spacing:0;">로그인 인증번호</h1>
                              <p style="margin:10px 0 0 0;font-size:15px;line-height:24px;color:#4e5968;">아래 4자리 숫자를 로그인 화면에 입력해주세요.</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:6px 28px 22px 28px;">
                              <div style="background:#f8fafc;border:1px solid #e5e8eb;border-radius:16px;padding:24px;text-align:center;">
                                <div style="font-size:13px;line-height:18px;color:#8b95a1;font-weight:700;margin-bottom:10px;">인증번호</div>
                                <div style="font-size:38px;line-height:44px;color:#0064ff;font-weight:900;letter-spacing:8px;font-family:ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,'Liberation Mono',monospace;">%s</div>
                              </div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 28px 28px 28px;">
                              <p style="margin:0;font-size:14px;line-height:22px;color:#4e5968;">유효 시간: <strong style="color:#191f28;">%s까지</strong></p>
                              <p style="margin:14px 0 0 0;font-size:13px;line-height:20px;color:#8b95a1;">본인이 요청하지 않았다면 이 메일을 무시해주세요. 이 인증번호는 보안을 위해 다른 사람에게 공유하지 마세요.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(appName, appName, escapedCode, formattedExpiresAt);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
