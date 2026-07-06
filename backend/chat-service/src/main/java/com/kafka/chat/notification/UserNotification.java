package com.kafka.chat.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_recipient_created", columnList = "recipient_email, created_at"),
                @Index(name = "idx_notifications_recipient_read", columnList = "recipient_email, read_at")
        }
)
public class UserNotification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "actor_email", nullable = false, length = 320)
    private String actorEmail;

    @Column(name = "actor_name", nullable = false, length = 120)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 500)
    private String body;

    @Column(name = "target_room_id", length = 80)
    private String targetRoomId;

    @Column(name = "target_message_id", length = 80)
    private String targetMessageId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    public UserNotification(
            String recipientEmail,
            String actorEmail,
            String actorName,
            NotificationType type,
            String title,
            String body,
            String targetRoomId,
            String targetMessageId
    ) {
        this.recipientEmail = recipientEmail;
        this.actorEmail = actorEmail;
        this.actorName = actorName;
        this.type = type;
        this.title = title;
        this.body = body;
        this.targetRoomId = targetRoomId;
        this.targetMessageId = targetMessageId;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead() {
        if (readAt == null) {
            readAt = Instant.now();
        }
    }
}
