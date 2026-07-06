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
        name = "chat_room_read_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_room_read_state_user", columnNames = {"room_id", "user_email"}),
        indexes = {
                @Index(name = "idx_chat_room_read_state_room", columnList = "room_id"),
                @Index(name = "idx_chat_room_read_state_user", columnList = "user_email")
        }
)
public class ChatRoomReadState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 80)
    private String roomId;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public ChatRoomReadState(String roomId, String userEmail, Instant lastReadAt) {
        this.roomId = roomId;
        this.userEmail = userEmail;
        this.lastReadAt = lastReadAt;
    }

    public void markRead(Instant readAt) {
        if (lastReadAt == null || readAt.isAfter(lastReadAt)) {
            this.lastReadAt = readAt;
        }
        this.updatedAt = Instant.now();
    }
}
