package com.kafka.auth.admin.audit;

import com.kafka.auth.admin.audit.AdminAuditDtos.AdminAuditEventListResponse;
import com.kafka.auth.admin.audit.AdminAuditDtos.AdminAuditEventResponse;
import com.kafka.auth.internal.AdminAuditRecordRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditEventRepository adminAuditEventRepository;

    @Transactional(readOnly = true)
    public AdminAuditEventListResponse recentEvents(int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        List<AdminAuditEventResponse> events = adminAuditEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, normalizedLimit))
                .stream()
                .map(this::toResponse)
                .toList();
        return new AdminAuditEventListResponse(normalizedLimit, events);
    }

    /**
     * Persists an audit event submitted by a trusted internal service (e.g. chat-service's
     * DLT replay). The submitting service builds the domain-specific summary/details; this
     * keeps the audit log central without auth-service depending on other services' domains.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AdminAuditRecordRequest request) {
        AdminAuditAction action = AdminAuditAction.valueOf(request.action());
        adminAuditEventRepository.save(new AdminAuditEvent(
                request.actorEmail(),
                request.actorName(),
                action,
                request.resourceType(),
                request.resourceId(),
                request.summary(),
                request.success(),
                request.detailsJson()
        ));
    }

    private AdminAuditEventResponse toResponse(AdminAuditEvent event) {
        return new AdminAuditEventResponse(
                event.getId(),
                event.getActorEmail(),
                event.getActorName(),
                event.getAction().name(),
                event.getResourceType(),
                event.getResourceId(),
                event.getSummary(),
                event.isSuccess(),
                event.getDetailsJson(),
                event.getCreatedAt()
        );
    }
}
