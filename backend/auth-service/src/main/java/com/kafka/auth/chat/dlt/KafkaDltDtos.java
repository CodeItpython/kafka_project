package com.kafka.auth.chat.dlt;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public final class KafkaDltDtos {
    private KafkaDltDtos() {
    }

    public record DltMessageResponse(
            String topic,
            int partition,
            long offset,
            String key,
            String messageId,
            String roomId,
            String roomName,
            String senderEmail,
            String senderName,
            Instant createdAt
    ) {
    }

    public record DltMessageListResponse(
            String topic,
            int requestedLimit,
            List<DltMessageResponse> messages
    ) {
    }

    public record DltReplayRequest(
            List<@NotBlank String> messageIds,
            @Min(1) @Max(100) Integer limit,
            boolean dryRun
    ) {
        public int normalizedLimit() {
            return limit == null ? 20 : limit;
        }
    }

    public record DltReplayResponse(
            String sourceTopic,
            String targetTopic,
            boolean dryRun,
            int scannedCount,
            int replayedCount,
            List<DltMessageResponse> replayedMessages
    ) {
    }
}
