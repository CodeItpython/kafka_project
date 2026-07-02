package com.kafka.auth.admin.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.admin.audit.AdminAuditDtos.AdminAuditEventListResponse;
import com.kafka.auth.admin.audit.AdminAuditDtos.AdminAuditEventResponse;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayResponse;
import com.kafka.auth.model.UserAccount;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditEventRepository adminAuditEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public AdminAuditEventListResponse recentEvents(int limit) {
        int normalizedLimit = Math.min(Math.max(limit, 1), 100);
        List<AdminAuditEventResponse> events = adminAuditEventRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, normalizedLimit))
                .stream()
                .map(this::toResponse)
                .toList();
        return new AdminAuditEventListResponse(normalizedLimit, events);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDltReplaySuccess(UserAccount actor, DltReplayRequest request, DltReplayResponse response) {
        AdminAuditAction action = actionFor(request);
        Map<String, Object> details = baseDltReplayDetails(request);
        details.put("sourceTopic", response.sourceTopic());
        details.put("targetTopic", response.targetTopic());
        details.put("scannedCount", response.scannedCount());
        details.put("replayedCount", response.replayedCount());
        details.put("replayedMessageIds", response.replayedMessages().stream().map(message -> message.messageId()).toList());
        details.put("createdAt", Instant.now().toString());

        adminAuditEventRepository.save(new AdminAuditEvent(
                actor.getEmail(),
                actor.getName(),
                action,
                "KAFKA_DLT",
                response.sourceTopic(),
                summaryFor(action, true, response.replayedCount()),
                true,
                toJson(details)
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDltReplayFailure(UserAccount actor, DltReplayRequest request, RuntimeException exception) {
        AdminAuditAction action = actionFor(request);
        Map<String, Object> details = baseDltReplayDetails(request);
        details.put("errorType", exception.getClass().getName());
        details.put("errorMessage", exception.getMessage());
        details.put("createdAt", Instant.now().toString());

        adminAuditEventRepository.save(new AdminAuditEvent(
                actor.getEmail(),
                actor.getName(),
                action,
                "KAFKA_DLT",
                "unknown",
                summaryFor(action, false, 0),
                false,
                toJson(details)
        ));
    }

    private Map<String, Object> baseDltReplayDetails(DltReplayRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dryRun", request.dryRun());
        details.put("requestedLimit", request.normalizedLimit());
        details.put("requestedMessageIds", request.messageIds() == null ? List.of() : request.messageIds());
        return details;
    }

    private AdminAuditAction actionFor(DltReplayRequest request) {
        return request.dryRun() ? AdminAuditAction.DLT_REPLAY_DRY_RUN : AdminAuditAction.DLT_REPLAY_EXECUTE;
    }

    private String summaryFor(AdminAuditAction action, boolean success, int affectedCount) {
        String verb = action == AdminAuditAction.DLT_REPLAY_DRY_RUN ? "DLT 재처리 미리 확인" : "DLT 재처리 실행";
        String result = success ? "성공" : "실패";
        return "%s %s, 대상 %d개".formatted(verb, result, affectedCount);
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("관리자 감사 로그 상세 정보를 JSON으로 변환하지 못했습니다.", exception);
        }
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
