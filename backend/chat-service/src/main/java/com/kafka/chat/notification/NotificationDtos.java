package com.kafka.chat.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record RegisterPushTokenRequest(
            @NotBlank String token,
            @NotNull PushPlatform platform
    ) {
    }

    public record NotificationResponse(
            Long id,
            String type,
            String title,
            String body,
            String actorEmail,
            String actorName,
            String targetRoomId,
            String targetMessageId,
            boolean read,
            Instant createdAt
    ) {
    }

    public record NotificationListResponse(
            List<NotificationResponse> notifications,
            long unreadCount
    ) {
    }

    public record NotificationSubscriptionResponse(
            String topic,
            long unreadCount
    ) {
    }
}
