package com.kafka.auth.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.fcm")
public record FcmProperties(
        boolean enabled,
        String projectId,
        String serviceAccountJson,
        String serviceAccountPath,
        boolean dryRun,
        String webPushLink,
        String webPushIcon
) {
}
