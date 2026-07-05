package com.kafka.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "chat_room_user_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_room_user_preference", columnNames = {"room_id", "user_email"}),
        indexes = {
                @Index(name = "idx_chat_room_user_preference_user", columnList = "user_email"),
                @Index(name = "idx_chat_room_user_preference_room", columnList = "room_id")
        }
)
public class ChatRoomUserPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean muted;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public ChatRoomUserPreference(String roomId, String userEmail) {
        this.roomId = roomId;
        this.userEmail = userEmail;
    }

    public void update(Boolean pinned, Boolean muted) {
        if (pinned != null) {
            this.pinned = pinned;
        }
        if (muted != null) {
            this.muted = muted;
        }
        this.updatedAt = Instant.now();
    }
}
