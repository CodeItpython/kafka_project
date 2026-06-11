package com.kafka.auth.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    private ChatRoomType type = ChatRoomType.GROUP;

    @Column(unique = true, length = 512)
    private String directKey;

    @ElementCollection
    @CollectionTable(name = "chat_room_participants", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "email", nullable = false)
    private Set<String> participantEmails = new LinkedHashSet<>();

    @ElementCollection
    @CollectionTable(name = "chat_room_hidden_users", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "email", nullable = false)
    private Set<String> hiddenForEmails = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChatRoom() {
    }

    public ChatRoom(String name, String description, String createdBy) {
        this.name = name;
        this.description = description;
        this.createdBy = createdBy;
        this.type = ChatRoomType.GROUP;
    }

    public static ChatRoom direct(String directKey, String name, String createdBy, Set<String> participantEmails) {
        ChatRoom room = new ChatRoom(name, "1:1 개인 채팅방", createdBy);
        room.type = ChatRoomType.DIRECT;
        room.directKey = directKey;
        room.participantEmails = new LinkedHashSet<>(participantEmails);
        return room;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public ChatRoomType getType() {
        return type == null ? ChatRoomType.GROUP : type;
    }

    public String getDirectKey() {
        return directKey;
    }

    public Set<String> getParticipantEmails() {
        return participantEmails;
    }

    public Set<String> getHiddenForEmails() {
        return hiddenForEmails;
    }

    public boolean isVisibleTo(String email) {
        return !hiddenForEmails.contains(email) && (getType() == ChatRoomType.GROUP || participantEmails.contains(email));
    }

    public void hideFor(String email) {
        hiddenForEmails.add(email);
    }

    public boolean isCreatedBy(String email) {
        return createdBy.equalsIgnoreCase(email);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
