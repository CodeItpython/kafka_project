package com.kafka.auth.notification;

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
        name = "push_device_tokens",
        indexes = {
                @Index(name = "idx_push_tokens_user_enabled", columnList = "user_email, enabled"),
                @Index(name = "idx_push_tokens_hash", columnList = "token_hash", unique = true)
        }
)
public class PushDeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 4096)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushPlatform platform;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();

    public PushDeviceToken(String userEmail, String tokenHash, String token, PushPlatform platform) {
        this.userEmail = userEmail;
        this.tokenHash = tokenHash;
        this.token = token;
        this.platform = platform;
    }

    public void refresh(String userEmail, String token, PushPlatform platform) {
        this.userEmail = userEmail;
        this.token = token;
        this.platform = platform;
        this.enabled = true;
        this.updatedAt = Instant.now();
        this.lastSeenAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }
}
