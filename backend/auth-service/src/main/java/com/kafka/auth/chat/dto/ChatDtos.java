package com.kafka.auth.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

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
            AttachmentRequest attachment,
            String replyToMessageId
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

    public record RoomPreferenceRequest(
            Boolean pinned,
            Boolean muted
    ) {
    }

    public record InviteRoomParticipantsRequest(
            @NotEmpty List<@NotBlank String> emails
    ) {
    }

    public record ChatRoomResponse(
            String id,
            String name,
            String description,
            String createdBy,
            String type,
            Instant createdAt,
            long unreadCount,
            boolean pinned,
            boolean muted,
            int participantCount
    ) {
    }

    public record ContactResponse(
            Long id,
            String email,
            String name,
            String provider,
            String statusMessage,
            String profileImageUrl,
            boolean online
    ) {
    }

    public record RoomParticipantResponse(
            Long id,
            String email,
            String name,
            String provider,
            String statusMessage,
            String profileImageUrl,
            boolean online,
            boolean owner
    ) {
    }

    public record TypingRequest(
            boolean typing
    ) {
    }

    public record MessageReactionRequest(
            @NotBlank @Size(max = 16) String emoji
    ) {
    }

    public record EditMessageRequest(
            @NotBlank @Size(max = 2000) String content
    ) {
    }

    public record MessageReactionResponse(
            String emoji,
            long count,
            boolean reactedByMe,
            java.util.List<String> reactorEmails
    ) {
    }

    public record RoomPresenceResponse(
            java.util.List<String> onlineUsers,
            java.util.List<String> typingUsers
    ) {
    }

    public record ReadReceiptResponse(
            String email,
            String name,
            String profileImageUrl,
            boolean online,
            Instant lastReadAt
    ) {
    }

    public record RoomReadSummaryResponse(
            String roomId,
            Instant currentUserLastReadAt,
            java.util.List<ReadReceiptResponse> receipts
    ) {
    }

    public record ConversationSummaryResponse(
            String summary,
            String model,
            Instant generatedAt,
            int messageCount
    ) {
    }

    public record SearchSuggestionResponse(
            String text,
            String type,
            String roomId,
            String roomName
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
            Instant createdAt,
            Instant editedAt,
            long readCount,
            String deliveryStatus,
            java.util.List<MessageReactionResponse> reactions,
            String replyToMessageId,
            String replyToSenderName,
            String replyToContent
    ) {
    }
}
