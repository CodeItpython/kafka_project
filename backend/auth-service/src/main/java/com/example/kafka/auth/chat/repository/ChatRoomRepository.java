package com.example.kafka.auth.chat.repository;

import com.example.kafka.auth.chat.model.ChatRoom;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, String> {
    List<ChatRoom> findTop20ByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name);

    List<ChatRoom> findTop20ByOrderByCreatedAtDesc();
}
