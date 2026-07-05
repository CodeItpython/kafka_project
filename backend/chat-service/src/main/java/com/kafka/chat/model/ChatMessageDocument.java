package com.kafka.chat.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageDocument {
    @Id
    private String id;

    @Indexed
    private String roomId;

    private String roomName;
    private String senderEmail;
    private String senderName;
    private String content;
    private String attachmentUrl;
    private String attachmentType;
    private String attachmentName;
    private Long attachmentSize;
    private String replyToMessageId;
    private String replyToSenderName;
    private String replyToContent;
    private boolean deletedForEveryone;
    private Instant editedAt;
    private Set<String> deletedForEmails = new LinkedHashSet<>();
    private Map<String, Set<String>> reactionEmailsByEmoji = new LinkedHashMap<>();

    @Indexed
    private Instant createdAt;

    public ChatMessageDocument(
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
            String replyToMessageId,
            String replyToSenderName,
            String replyToContent,
            Instant createdAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.roomName = roomName;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.content = content;
        this.attachmentUrl = attachmentUrl;
        this.attachmentType = attachmentType;
        this.attachmentName = attachmentName;
        this.attachmentSize = attachmentSize;
        this.replyToMessageId = replyToMessageId;
        this.replyToSenderName = replyToSenderName;
        this.replyToContent = replyToContent;
        this.createdAt = createdAt;
    }

    public ChatMessageDocument(
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
            Instant createdAt
    ) {
        this(
                id,
                roomId,
                roomName,
                senderEmail,
                senderName,
                content,
                attachmentUrl,
                attachmentType,
                attachmentName,
                attachmentSize,
                null,
                null,
                null,
                createdAt
        );
    }

    public boolean isVisibleTo(String email) {
        return !deletedForEmails.contains(email);
    }

    public void hideFor(String email) {
        deletedForEmails.add(email);
    }

    public void deleteForEveryone() {
        deletedForEveryone = true;
        content = "삭제된 메시지입니다.";
        attachmentUrl = null;
        attachmentType = null;
        attachmentName = null;
        attachmentSize = null;
        reactionEmailsByEmoji.clear();
    }

    public void editContent(String content) {
        this.content = content;
        this.editedAt = Instant.now();
    }

    public boolean toggleReaction(String emoji, String email) {
        Set<String> emails = reactionEmailsByEmoji.computeIfAbsent(emoji, key -> new LinkedHashSet<>());
        boolean added = emails.add(email);
        if (!added) {
            emails.remove(email);
            if (emails.isEmpty()) {
                reactionEmailsByEmoji.remove(emoji);
            }
        }
        return added;
    }

}
