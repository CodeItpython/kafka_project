package com.kafka.auth.chat.dto;

import java.time.Instant;

public record ChatMessageEvent(
        String messageId,
        String roomId,
        String roomName,
        String senderEmail,
        String senderName,
        String content,
        String attachmentUrl,
        String attachmentType,
        String attachmentName,
        Long attachmentSize,
        String replyToMessageId,
        String replyToSenderName,
        String replyToContent,
        Instant createdAt
) {
}
