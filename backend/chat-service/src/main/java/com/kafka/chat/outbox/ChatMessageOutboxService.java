package com.kafka.chat.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.chat.dto.ChatMessageEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageOutboxService {
    public static final String AGGREGATE_TYPE = "ChatMessage";
    public static final String EVENT_TYPE = "ChatMessageCreated";

    private final ObjectMapper objectMapper;
    private final OutboxEventRepository outboxEventRepository;

    @Transactional
    public void append(ChatMessageEvent event, String topic) {
        outboxEventRepository.save(new OutboxEvent(
                AGGREGATE_TYPE,
                event.messageId(),
                EVENT_TYPE,
                event.roomId(),
                topic,
                toPayload(event),
                event.createdAt()
        ));
    }

    private String toPayload(ChatMessageEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("채팅 메시지 이벤트를 outbox payload로 변환할 수 없습니다.", exception);
        }
    }
}
