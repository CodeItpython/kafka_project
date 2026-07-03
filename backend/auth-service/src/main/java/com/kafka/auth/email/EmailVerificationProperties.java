package com.kafka.auth.email;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email.verification")
public class EmailVerificationProperties {
    private String from = "";
    private String subject = "[Kafka Talk] 이메일 인증코드";
    private String appName = "Kafka Talk";
    private Duration ttl = Duration.ofMinutes(5);
    private Duration resendCooldown = Duration.ofSeconds(60);
    private int maxVerifyAttempts = 5;
    private Duration verifyAttemptWindow = Duration.ofMinutes(10);

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getResendCooldown() {
        return resendCooldown;
    }

    public void setResendCooldown(Duration resendCooldown) {
        this.resendCooldown = resendCooldown;
    }

    public int getMaxVerifyAttempts() {
        return maxVerifyAttempts;
    }

    public void setMaxVerifyAttempts(int maxVerifyAttempts) {
        this.maxVerifyAttempts = maxVerifyAttempts;
    }

    public Duration getVerifyAttemptWindow() {
        return verifyAttemptWindow;
    }

    public void setVerifyAttemptWindow(Duration verifyAttemptWindow) {
        this.verifyAttemptWindow = verifyAttemptWindow;
    }
}
