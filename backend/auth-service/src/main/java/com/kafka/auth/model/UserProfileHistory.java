package com.kafka.auth.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "user_profile_history",
        indexes = {
                @Index(name = "idx_profile_history_user_created", columnList = "user_id, created_at")
        }
)
public class UserProfileHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String statusMessage;

    @Column(length = 1000)
    private String profileImageUrl;

    @Column(nullable = false)
    private String eventType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserProfileHistory(
            Long userId,
            String name,
            String statusMessage,
            String profileImageUrl,
            String eventType
    ) {
        this.userId = userId;
        this.name = name;
        this.statusMessage = statusMessage == null ? "" : statusMessage;
        this.profileImageUrl = profileImageUrl;
        this.eventType = eventType;
    }
}
