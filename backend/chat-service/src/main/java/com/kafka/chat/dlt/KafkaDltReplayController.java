package com.kafka.chat.dlt;

import com.kafka.chat.client.AdminAuditClient;
import com.kafka.chat.dlt.KafkaDltDtos.DltMessageListResponse;
import com.kafka.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.chat.dlt.KafkaDltDtos.DltReplayResponse;
import com.kafka.chat.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/kafka/dlt")
@RequiredArgsConstructor
@Validated
public class KafkaDltReplayController {
    private final KafkaDltReplayService kafkaDltReplayService;
    private final AdminAuditClient adminAuditClient;

    @GetMapping("/messages")
    public ResponseEntity<DltMessageListResponse> messages(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(kafkaDltReplayService.messages(limit));
    }

    @PostMapping("/replay")
    public ResponseEntity<DltReplayResponse> replay(
            @AuthenticationPrincipal AuthUser actor,
            @Valid @RequestBody DltReplayRequest request
    ) {
        try {
            DltReplayResponse response = kafkaDltReplayService.replay(request);
            adminAuditClient.recordDltReplaySuccess(actor, request, response);
            return ResponseEntity.ok(response);
        } catch (RuntimeException exception) {
            adminAuditClient.recordDltReplayFailure(actor, request, exception);
            throw exception;
        }
    }
}
