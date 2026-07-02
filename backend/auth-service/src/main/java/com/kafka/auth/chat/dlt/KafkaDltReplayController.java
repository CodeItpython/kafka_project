package com.kafka.auth.chat.dlt;

import com.kafka.auth.admin.audit.AdminAuditService;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltMessageListResponse;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayResponse;
import com.kafka.auth.model.UserAccount;
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
    private final AdminAuditService adminAuditService;

    @GetMapping("/messages")
    public ResponseEntity<DltMessageListResponse> messages(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(kafkaDltReplayService.messages(limit));
    }

    @PostMapping("/replay")
    public ResponseEntity<DltReplayResponse> replay(
            @AuthenticationPrincipal UserAccount actor,
            @Valid @RequestBody DltReplayRequest request
    ) {
        try {
            DltReplayResponse response = kafkaDltReplayService.replay(request);
            adminAuditService.recordDltReplaySuccess(actor, request, response);
            return ResponseEntity.ok(response);
        } catch (RuntimeException exception) {
            adminAuditService.recordDltReplayFailure(actor, request, exception);
            throw exception;
        }
    }
}
