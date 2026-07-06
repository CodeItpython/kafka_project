package com.kafka.chat.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.outbox.relay")
public record OutboxRelayProperties(
        boolean enabled,
        int batchSize,
        int maxAttempts,
        long sendTimeoutMs
) {
    public OutboxRelayProperties {
        if (batchSize <= 0) {
            batchSize = 20;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 10;
        }
        if (sendTimeoutMs <= 0) {
            sendTimeoutMs = 5000;
        }
    }
}
