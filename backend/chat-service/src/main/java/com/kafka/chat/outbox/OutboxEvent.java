package com.kafka.chat.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "outbox_events",
        indexes = {
                @Index(name = "idx_outbox_events_status_next_attempt", columnList = "status,next_attempt_at"),
                @Index(name = "idx_outbox_events_aggregate", columnList = "aggregatetype,aggregateid"),
                @Index(name = "idx_outbox_events_created_at", columnList = "created_at")
        }
)
public class OutboxEvent {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "aggregatetype", nullable = false, length = 120)
    private String aggregateType;

    @Column(name = "aggregateid", nullable = false, length = 120)
    private String aggregateId;

    @Column(name = "type", nullable = false, length = 160)
    private String type;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(nullable = false, length = 160)
    private String topic;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public OutboxEvent(
            String aggregateType,
            String aggregateId,
            String type,
            String eventKey,
            String topic,
            String payload,
            Instant timestamp
    ) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.type = type;
        this.eventKey = eventKey;
        this.topic = topic;
        this.payload = payload;
        this.timestamp = timestamp;
        this.nextAttemptAt = timestamp;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        if (timestamp == null) {
            timestamp = now;
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = Instant.now();
        lastError = null;
    }

    public void markFailed(RuntimeException exception, int maxAttempts) {
        attempts++;
        lastError = truncate(exception.getMessage());
        if (attempts >= maxAttempts) {
            status = OutboxEventStatus.DEAD;
            return;
        }
        status = OutboxEventStatus.FAILED;
        nextAttemptAt = Instant.now().plus(backoff(attempts));
    }

    private Duration backoff(int attemptCount) {
        long seconds = Math.min(60, Math.max(1, attemptCount) * 5L);
        return Duration.ofSeconds(seconds);
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
