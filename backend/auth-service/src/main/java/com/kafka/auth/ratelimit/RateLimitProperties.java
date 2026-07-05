package com.kafka.auth.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private int authLimit = 20;
    private int apiLimit = 600;
    private Duration window = Duration.ofMinutes(1);
    /**
     * Only trust the X-Forwarded-For header when a trusted reverse proxy sits in
     * front of this service. When false (default) the direct peer address is used,
     * so a client cannot spoof the header to get a fresh rate-limit bucket.
     */
    private boolean trustForwardedFor = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    public void setTrustForwardedFor(boolean trustForwardedFor) {
        this.trustForwardedFor = trustForwardedFor;
    }

    public int getAuthLimit() {
        return authLimit;
    }

    public void setAuthLimit(int authLimit) {
        this.authLimit = authLimit;
    }

    public int getApiLimit() {
        return apiLimit;
    }

    public void setApiLimit(int apiLimit) {
        this.apiLimit = apiLimit;
    }

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}
