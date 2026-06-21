package com.kafka.auth.outbox;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD
}
