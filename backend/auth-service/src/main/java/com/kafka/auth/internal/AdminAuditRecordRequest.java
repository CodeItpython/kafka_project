package com.kafka.auth.internal;

/**
 * Generic admin-audit record submitted by a trusted internal service. The
 * submitting service owns the domain formatting (summary/details); auth-service
 * just persists the row so the central audit log stays complete.
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
