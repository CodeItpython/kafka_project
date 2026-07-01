package com.kafka.auth.chat.dlt;

import com.kafka.auth.chat.dlt.KafkaDltDtos.DltMessageListResponse;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayRequest;
import com.kafka.auth.chat.dlt.KafkaDltDtos.DltReplayResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/messages")
    public ResponseEntity<DltMessageListResponse> messages(
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(kafkaDltReplayService.messages(limit));
    }

    @PostMapping("/replay")
    public ResponseEntity<DltReplayResponse> replay(@Valid @RequestBody DltReplayRequest request) {
        return ResponseEntity.ok(kafkaDltReplayService.replay(request));
    }
}
