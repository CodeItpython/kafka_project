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
            String content,
            AttachmentRequest attachment
    ) {
    }

    public record AttachmentRequest(
            @NotBlank String url,
            @NotBlank String type,
            @NotBlank String name,
            Long size
    ) {
    }

    public record AttachmentResponse(
            String url,
            String type,
            String name,
            long size
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
            String attachmentUrl,
            String attachmentType,
            String attachmentName,
            Long attachmentSize,
            boolean deletedForEveryone,
            Instant createdAt
    ) {
    }
}
