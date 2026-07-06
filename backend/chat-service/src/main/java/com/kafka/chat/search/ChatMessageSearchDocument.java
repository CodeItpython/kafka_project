package com.kafka.chat.search;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "chat-messages", createIndex = false)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageSearchDocument {
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String roomId;

    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String roomName;

    @Field(type = FieldType.Keyword)
    private String senderEmail;

    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String senderName;

    @Field(type = FieldType.Search_As_You_Type, maxShingleSize = 3)
    private String content;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;

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
}
