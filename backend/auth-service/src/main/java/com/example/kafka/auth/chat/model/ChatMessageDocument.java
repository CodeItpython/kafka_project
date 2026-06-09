package com.example.kafka.auth.chat.model;

import java.time.Instant;
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
            Instant createdAt
    ) {
        this.id = id;
        this.roomId = roomId;
        this.roomName = roomName;
        this.senderEmail = senderEmail;
        this.senderName = senderName;
        this.content = content;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
