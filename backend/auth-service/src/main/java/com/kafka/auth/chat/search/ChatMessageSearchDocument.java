package com.kafka.auth.chat.search;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "chat-messages")
public class ChatMessageSearchDocument {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String roomId;

    @Field(type = FieldType.Text)
    private String roomName;

    @Field(type = FieldType.Keyword)
    private String senderEmail;

    @Field(type = FieldType.Text)
    private String senderName;

    @Field(type = FieldType.Text)
    private String content;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

    protected ChatMessageSearchDocument() {
    }

    public ChatMessageSearchDocument(
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
