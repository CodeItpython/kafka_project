package com.kafka.auth.chat.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "chat_messages")
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
    private boolean deletedForEveryone;
    private Set<String> deletedForEmails = new LinkedHashSet<>();

    @Indexed
    private Instant createdAt;

    protected ChatMessageDocument() {
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
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getRoomName() {
        return roomName;
    }

    public String getSenderEmail() {
        return senderEmail;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getContent() {
        return content;
    }

    public String getAttachmentUrl() {
        return attachmentUrl;
    }

    public String getAttachmentType() {
        return attachmentType;
    }

    public String getAttachmentName() {
        return attachmentName;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public boolean isDeletedForEveryone() {
        return deletedForEveryone;
    }

    public Set<String> getDeletedForEmails() {
        return deletedForEmails;
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
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
