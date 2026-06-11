package com.example.kafka.auth.chat.controller;

import com.example.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.ContactResponse;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.example.kafka.auth.chat.dto.ChatDtos.AttachmentResponse;
import com.example.kafka.auth.chat.dto.ChatMessageEvent;
import com.example.kafka.auth.chat.service.ChatService;
import com.example.kafka.auth.model.UserAccount;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> rooms(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.rooms(query, user));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.createRoom(request, user));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> hideRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        chatService.hideRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/direct-rooms")
    public ResponseEntity<ChatRoomResponse> directRoom(
            @Valid @RequestBody CreateDirectRoomRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.findOrCreateDirectRoom(request, user));
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactResponse>> contacts(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.contacts(query, user));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.messages(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageEvent> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.accepted().body(chatService.publishMessage(roomId, request, user));
    }

    @PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.storeAttachment(file));
    }

    @GetMapping("/attachments/{fileName:.+}")
    public ResponseEntity<Resource> attachment(@PathVariable String fileName) throws IOException {
        Resource resource = chatService.loadAttachment(fileName);
        String contentType = resource.getURL().openConnection().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .body(resource);
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}/me")
    public ResponseEntity<ChatMessageResponse> hideMessageForMe(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.hideMessageForMe(roomId, messageId, user));
    }

    @DeleteMapping("/rooms/{roomId}/messages/me")
    public ResponseEntity<Void> hideRoomMessagesForMe(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        chatService.hideRoomMessagesForMe(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}/everyone")
    public ResponseEntity<ChatMessageResponse> deleteMessageForEveryone(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.deleteMessageForEveryone(roomId, messageId, user));
    }

    @GetMapping("/messages/search")
    public ResponseEntity<List<ChatMessageResponse>> searchMessages(
            @RequestParam String query,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.searchMessages(query, user));
    }
}
