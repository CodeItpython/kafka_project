package com.kafka.auth.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailVerificationMailServiceTest {
    @Mock
    private JavaMailSender mailSender;

    @Test
    void sendVerificationCodeEncodesKoreanSubjectAsMimeHeader() throws Exception {
        MailProperties mailProperties = new MailProperties();
        mailProperties.setHost("smtp.gmail.com");
        mailProperties.setUsername("sender@example.com");
        EmailVerificationProperties properties = new EmailVerificationProperties();
        properties.setSubject("[Kafka Talk] 로그인 인증번호");
        EmailVerificationMailService service = new EmailVerificationMailService(mailSender, mailProperties, properties);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        service.sendVerificationCode("receiver@example.com", "1234", Instant.parse("2026-07-03T07:00:00Z"));

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        String rawSubjectHeader = messageCaptor.getValue().getHeader("Subject", null);
        assertThat(rawSubjectHeader).startsWith("=?UTF-8?B?");
        assertThat(messageCaptor.getValue().getSubject()).isEqualTo("[Kafka Talk] 로그인 인증번호");
    }
}
