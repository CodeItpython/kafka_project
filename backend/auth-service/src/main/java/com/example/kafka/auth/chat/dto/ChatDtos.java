package com.example.kafka.auth.chat.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class ChatDtos {
    private ChatDtos() {
    }

    public record CreateRoomRequest(
            @NotBlank String name,
            String description
    ) {
    }

    public record SendMessageRequest(
            @NotBlank String content
    ) {
    }

    public record CreateDirectRoomRequest(
            @NotBlank String partnerEmail
    ) {
    }

    public record ChatRoomResponse(
            String id,
            String name,
            String description,
            String createdBy,
            String type,
            Instant createdAt
    ) {
    }

    public record ContactResponse(
            Long id,
            String email,
            String name,
            String provider
    ) {
    }

    public record ChatMessageResponse(
            String id,
            String roomId,
            String roomName,
            String senderEmail,
            String senderName,
            String content,
            Instant createdAt
    ) {
    }
}
