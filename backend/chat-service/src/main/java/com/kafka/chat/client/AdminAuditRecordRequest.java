package com.kafka.chat.client;

/**
 * Generic admin-audit record posted to auth-service. chat-service builds the
 * DLT-specific summary/details itself and auth-service just persists the row,
 * keeping the central audit log intact without auth-service knowing about chat.
 */
public record AdminAuditRecordRequest(
        String actorEmail,
        String actorName,
        String action,
        String resourceType,
        String resourceId,
        String summary,
        boolean success,
        String detailsJson
) {
}
