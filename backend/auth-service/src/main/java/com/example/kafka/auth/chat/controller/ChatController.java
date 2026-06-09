package com.example.kafka.auth.chat.controller;

import com.example.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.example.kafka.auth.chat.dto.ChatMessageEvent;
import com.example.kafka.auth.chat.service.ChatService;
import com.example.kafka.auth.model.UserAccount;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> rooms(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(chatService.rooms(query));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.createRoom(request, user));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(@PathVariable String roomId) {
        return ResponseEntity.ok(chatService.messages(roomId));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageEvent> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.accepted().body(chatService.publishMessage(roomId, request, user));
    }

    @GetMapping("/messages/search")
    public ResponseEntity<List<ChatMessageResponse>> searchMessages(@RequestParam String query) {
        return ResponseEntity.ok(chatService.searchMessages(query));
    }
}
