package com.kafka.auth.chat.controller;

import com.kafka.auth.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.auth.chat.dto.ChatDtos.ChatRoomResponse;
import com.kafka.auth.chat.dto.ChatDtos.ContactResponse;
import com.kafka.auth.chat.dto.ChatDtos.ConversationSummaryResponse;
import com.kafka.auth.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.CreateRoomRequest;
import com.kafka.auth.chat.dto.ChatDtos.RoomPresenceResponse;
import com.kafka.auth.chat.dto.ChatDtos.SearchSuggestionResponse;
import com.kafka.auth.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.auth.chat.dto.ChatDtos.AttachmentResponse;
import com.kafka.auth.chat.dto.ChatDtos.TypingRequest;
import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.chat.service.ChatService;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.storage.StoredObject;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@RequiredArgsConstructor
@Validated
public class ChatController {
    private final ChatService chatService;

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

    @PostMapping("/presence/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal UserAccount user) {
        chatService.heartbeat(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/presence")
    public ResponseEntity<RoomPresenceResponse> roomPresence(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.roomPresence(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/typing")
    public ResponseEntity<Void> typing(
            @PathVariable String roomId,
            @RequestBody TypingRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        chatService.setTyping(roomId, request.typing(), user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SearchSuggestionResponse>> suggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "all") String scope,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.searchSuggestions(query, scope, user));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.messages(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/summary")
    public ResponseEntity<ConversationSummaryResponse> summarizeRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(chatService.summarizeRoom(roomId, user));
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
    public ResponseEntity<Resource> attachment(@PathVariable String fileName) {
        StoredObject storedObject = chatService.loadAttachment(fileName);
        String contentType = storedObject.contentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .body(storedObject.resource());
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
