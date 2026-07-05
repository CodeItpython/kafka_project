package com.kafka.chat.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.chat.dlt.KafkaDltDtos.DltMessageResponse;
import com.kafka.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.chat.dlt.KafkaDltDtos.DltReplayResponse;
import com.kafka.chat.security.AuthUser;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Records DLT-replay actions in auth-service's central admin audit log over REST.
 * chat-service builds the summary/details (the DLT domain knowledge lives here);
 * auth-service just persists the row. Audit writes are best-effort — a failure to
 * record must never fail the replay itself, so exceptions are logged and swallowed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAuditClient {
    private static final String ACTION_DRY_RUN = "DLT_REPLAY_DRY_RUN";
    private static final String ACTION_EXECUTE = "DLT_REPLAY_EXECUTE";

    private final AuthAdminAuditClient client;
    private final ObjectMapper objectMapper;

    public void recordDltReplaySuccess(AuthUser actor, DltReplayRequest request, DltReplayResponse response) {
        String action = actionFor(request);
        Map<String, Object> details = baseDetails(request);
        details.put("sourceTopic", response.sourceTopic());
        details.put("targetTopic", response.targetTopic());
        details.put("scannedCount", response.scannedCount());
        details.put("replayedCount", response.replayedCount());
        details.put("replayedMessageIds", response.replayedMessages().stream().map(DltMessageResponse::messageId).toList());
        details.put("createdAt", Instant.now().toString());
        post(actor, action, response.sourceTopic(), summaryFor(action, true, response.replayedCount()), true, details);
    }

    public void recordDltReplayFailure(AuthUser actor, DltReplayRequest request, RuntimeException exception) {
        String action = actionFor(request);
        Map<String, Object> details = baseDetails(request);
        details.put("errorType", exception.getClass().getName());
        details.put("errorMessage", exception.getMessage());
        details.put("createdAt", Instant.now().toString());
        post(actor, action, "unknown", summaryFor(action, false, 0), false, details);
    }

    private void post(AuthUser actor, String action, String resourceId, String summary, boolean success, Map<String, Object> details) {
        try {
            client.record(new AdminAuditRecordRequest(
                    actor.getEmail(),
                    actor.getName(),
                    action,
                    "KAFKA_DLT",
                    resourceId,
                    summary,
                    success,
                    toJson(details)
            ));
        } catch (RuntimeException exception) {
            log.warn("Unable to record admin audit event in auth-service. action={} continuing anyway.", action, exception);
        }
    }

    private Map<String, Object> baseDetails(DltReplayRequest request) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dryRun", request.dryRun());
        details.put("requestedLimit", request.normalizedLimit());
        details.put("requestedMessageIds", request.messageIds() == null ? List.of() : request.messageIds());
        return details;
    }

    private String actionFor(DltReplayRequest request) {
        return request.dryRun() ? ACTION_DRY_RUN : ACTION_EXECUTE;
    }

    private String summaryFor(String action, boolean success, int affectedCount) {
        String verb = ACTION_DRY_RUN.equals(action) ? "DLT 재처리 미리 확인" : "DLT 재처리 실행";
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
}
