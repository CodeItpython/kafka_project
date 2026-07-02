package com.kafka.auth.admin.audit;

import java.time.Instant;
import java.util.List;

public final class AdminAuditDtos {
    private AdminAuditDtos() {
    }

    public record AdminAuditEventResponse(
            Long id,
            String actorEmail,
            String actorName,
            String action,
            String resourceType,
            String resourceId,
            String summary,
            boolean success,
            String detailsJson,
            Instant createdAt
    ) {
    }

    public record AdminAuditEventListResponse(
            int requestedLimit,
            List<AdminAuditEventResponse> events
    ) {
    }
}
