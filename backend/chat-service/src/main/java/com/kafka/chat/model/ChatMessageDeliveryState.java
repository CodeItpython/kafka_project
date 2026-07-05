package com.kafka.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "chat_message_delivery_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_chat_message_delivery_state_user",
                columnNames = {"message_id", "user_email"}
        ),
        indexes = {
                @Index(name = "idx_chat_message_delivery_state_message", columnList = "message_id"),
                @Index(name = "idx_chat_message_delivery_state_room_user", columnList = "room_id, user_email"),
                @Index(name = "idx_chat_message_delivery_state_status", columnList = "status")
        }
)
public class ChatMessageDeliveryState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 80)
    private String messageId;

    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ChatMessageDeliveryStatus status = ChatMessageDeliveryStatus.SENT;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ChatMessageDeliveryState(String messageId, String roomId, String userEmail) {
        this.messageId = messageId;
        this.roomId = roomId;
        this.userEmail = userEmail;
    }

    public void markDelivered(Instant deliveredAt) {
        if (status == ChatMessageDeliveryStatus.SENT) {
            status = ChatMessageDeliveryStatus.DELIVERED;
        }
        if (this.deliveredAt == null || deliveredAt.isBefore(this.deliveredAt)) {
            this.deliveredAt = deliveredAt;
        }
        updatedAt = Instant.now();
    }

    public void markRead(Instant readAt) {
        status = ChatMessageDeliveryStatus.READ;
        if (deliveredAt == null || readAt.isBefore(deliveredAt)) {
            deliveredAt = readAt;
        }
        if (this.readAt == null || readAt.isAfter(this.readAt)) {
            this.readAt = readAt;
        }
        updatedAt = Instant.now();
    }
}
