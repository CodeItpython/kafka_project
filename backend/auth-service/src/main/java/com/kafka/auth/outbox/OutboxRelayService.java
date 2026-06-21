package com.kafka.auth.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.chat.service.ChatMetricsService;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayService {
    private static final Set<OutboxEventStatus> READY_STATUSES = Set.of(OutboxEventStatus.PENDING, OutboxEventStatus.FAILED);

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, ChatMessageEvent> kafkaTemplate;
    private final ChatMetricsService chatMetricsService;
    private final OutboxRelayProperties properties;

    @Transactional
    public int publishReadyEvents() {
        List<OutboxEvent> events = outboxEventRepository.findReadyEvents(
                READY_STATUSES,
                Instant.now(),
                PageRequest.of(0, properties.batchSize())
        );
        events.forEach(this::publish);
        return events.size();
    }

    private void publish(OutboxEvent event) {
        if (!ChatMessageOutboxService.EVENT_TYPE.equals(event.getType())) {
            event.markFailed(new IllegalStateException("지원하지 않는 outbox event type입니다: " + event.getType()), properties.maxAttempts());
            return;
        }

        ChatMessageEvent chatMessageEvent;
        try {
            chatMessageEvent = toChatMessageEvent(event);
        } catch (RuntimeException exception) {
            event.markFailed(exception, properties.maxAttempts());
            log.warn("Failed to deserialize outbox payload. eventId={}, attempts={}", event.getId(), event.getAttempts(), exception);
            return;
        }

        Timer.Sample sample = chatMetricsService.startTimer();
        try {
            kafkaTemplate.send(event.getTopic(), event.getEventKey(), chatMessageEvent)
                    .get(properties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            event.markPublished();
            chatMetricsService.recordKafkaPublishSuccess(sample, chatMessageEvent);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            RuntimeException failure = new IllegalStateException("outbox 이벤트 Kafka 발행이 중단되었습니다.", exception);
            event.markFailed(failure, properties.maxAttempts());
            chatMetricsService.recordKafkaPublishFailure(sample, chatMessageEvent);
            log.warn("Interrupted while publishing outbox event. eventId={}", event.getId(), failure);
        } catch (Exception exception) {
            RuntimeException failure = exception instanceof RuntimeException runtimeException
                    ? runtimeException
                    : new IllegalStateException("outbox 이벤트 Kafka 발행에 실패했습니다.", exception);
            event.markFailed(failure, properties.maxAttempts());
            chatMetricsService.recordKafkaPublishFailure(sample, chatMessageEvent);
            log.warn("Failed to publish outbox event. eventId={}, attempts={}", event.getId(), event.getAttempts(), failure);
        }
    }

    private ChatMessageEvent toChatMessageEvent(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), ChatMessageEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("outbox payload를 채팅 메시지 이벤트로 변환할 수 없습니다.", exception);
        }
    }
}
