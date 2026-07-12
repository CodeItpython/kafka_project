package com.kafka.chat.controller;

import com.kafka.chat.dto.ChatDtos.ChatMessageResponse;
import com.kafka.chat.dto.ChatDtos.ChatRoomResponse;
import com.kafka.chat.dto.ChatDtos.ContactResponse;
import com.kafka.chat.dto.ChatDtos.ConversationSummaryResponse;
import com.kafka.chat.dto.ChatDtos.CreateDirectRoomRequest;
import com.kafka.chat.dto.ChatDtos.CreateRoomRequest;
import com.kafka.chat.dto.ChatDtos.EditMessageRequest;
import com.kafka.chat.dto.ChatDtos.InviteRoomParticipantsRequest;
import com.kafka.chat.dto.ChatDtos.MessageDeliverySummaryResponse;
import com.kafka.chat.dto.ChatDtos.MessageReactionRequest;
import com.kafka.chat.dto.ChatDtos.RoomPresenceResponse;
import com.kafka.chat.dto.ChatDtos.RoomParticipantResponse;
import com.kafka.chat.dto.ChatDtos.RoomPreferenceRequest;
import com.kafka.chat.dto.ChatDtos.RoomReadSummaryResponse;
import com.kafka.chat.dto.ChatDtos.SearchSuggestionResponse;
import com.kafka.chat.dto.ChatDtos.SendMessageRequest;
import com.kafka.chat.dto.ChatDtos.AttachmentResponse;
import com.kafka.chat.dto.ChatDtos.TypingRequest;
import com.kafka.chat.dto.ChatMessageEvent;
import com.kafka.chat.service.ChatService;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.storage.StorageUrlSigner;
import com.kafka.chat.storage.StoredObject;
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
import org.springframework.web.bind.annotation.PatchMapping;
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
    private final StorageUrlSigner storageUrlSigner;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoomResponse>> rooms(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.rooms(query, user));
    }

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.createRoom(request, user));
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> hideRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        chatService.hideRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rooms/{roomId}/preferences")
    public ResponseEntity<ChatRoomResponse> updateRoomPreference(
            @PathVariable String roomId,
            @RequestBody RoomPreferenceRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.updateRoomPreference(roomId, request, user));
    }

    @PostMapping("/direct-rooms")
    public ResponseEntity<ChatRoomResponse> directRoom(
            @Valid @RequestBody CreateDirectRoomRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.findOrCreateDirectRoom(request, user));
    }

    @GetMapping("/rooms/{roomId}/participants")
    public ResponseEntity<List<RoomParticipantResponse>> roomParticipants(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.roomParticipants(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/participants")
    public ResponseEntity<List<RoomParticipantResponse>> inviteRoomParticipants(
            @PathVariable String roomId,
            @Valid @RequestBody InviteRoomParticipantsRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.inviteRoomParticipants(roomId, request, user));
    }

    @DeleteMapping("/rooms/{roomId}/participants/me")
    public ResponseEntity<Void> leaveRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        chatService.leaveRoom(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/contacts")
    public ResponseEntity<List<ContactResponse>> contacts(
            @RequestParam(required = false) String query,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.contacts(query, user));
    }

    @PostMapping("/presence/heartbeat")
    public ResponseEntity<Void> heartbeat(@AuthenticationPrincipal AuthUser user) {
        chatService.heartbeat(user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/rooms/{roomId}/presence")
    public ResponseEntity<RoomPresenceResponse> roomPresence(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.roomPresence(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/typing")
    public ResponseEntity<Void> typing(
            @PathVariable String roomId,
            @RequestBody TypingRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        chatService.setTyping(roomId, request.typing(), user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/suggestions")
    public ResponseEntity<List<SearchSuggestionResponse>> suggestions(
            @RequestParam String query,
            @RequestParam(defaultValue = "all") String scope,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.searchSuggestions(query, scope, user));
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> messages(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.messages(roomId, user));
    }

    @GetMapping("/rooms/{roomId}/read-receipts")
    public ResponseEntity<RoomReadSummaryResponse> readReceipts(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.readReceipts(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<RoomReadSummaryResponse> markRead(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.markRead(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/messages/{messageId}/delivered")
    public ResponseEntity<MessageDeliverySummaryResponse> markMessageDelivered(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.markMessageDelivered(roomId, messageId, user));
    }

    @PostMapping("/rooms/{roomId}/summary")
    public ResponseEntity<ConversationSummaryResponse> summarizeRoom(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.summarizeRoom(roomId, user));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ChatMessageEvent> sendMessage(
            @PathVariable String roomId,
            @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.accepted().body(chatService.publishMessage(roomId, request, user));
    }

    @PostMapping(path = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
            @RequestParam MultipartFile file,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.storeAttachment(file));
    }

    // 인라인 렌더가 안전한 이미지 타입. svg는 스크립트를 품을 수 있어 제외(→ 다운로드 처리).
    private static final java.util.Set<String> INLINE_IMAGE_TYPES =
            java.util.Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    @GetMapping("/attachments/{fileName:.+}")
    public ResponseEntity<Resource> attachment(
            @PathVariable String fileName,
            @RequestParam(required = false) Long exp,
            @RequestParam(required = false) String sig
    ) {
        if (!storageUrlSigner.isValid("/api/chat/attachments/" + fileName, exp, sig)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        StoredObject storedObject = chatService.loadAttachment(fileName);
        String contentType = storedObject.contentType();
        // 이미지(raster)·동영상·오디오만 인라인 허용. 그 외(html/svg/일반 파일)는 attachment로 내려받게 해서
        // 업로드 콘텐츠가 앱 오리진에서 실행되는 XSS를 막는다. nosniff로 MIME 스니핑도 차단.
        // 오디오는 음성 메시지 재생을 위해 <audio>로 스트리밍되어야 하므로 인라인 대상에 포함한다.
        boolean inlineSafe = contentType != null
                && (contentType.startsWith("video/") || contentType.startsWith("audio/") || INLINE_IMAGE_TYPES.contains(contentType));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, inlineSafe ? "inline" : "attachment")
                .header("X-Content-Type-Options", "nosniff")
                .body(storedObject.resource());
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}/me")
    public ResponseEntity<ChatMessageResponse> hideMessageForMe(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.hideMessageForMe(roomId, messageId, user));
    }

    @DeleteMapping("/rooms/{roomId}/messages/me")
    public ResponseEntity<Void> hideRoomMessagesForMe(
            @PathVariable String roomId,
            @AuthenticationPrincipal AuthUser user
    ) {
        chatService.hideRoomMessagesForMe(roomId, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}/everyone")
    public ResponseEntity<ChatMessageResponse> deleteMessageForEveryone(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.deleteMessageForEveryone(roomId, messageId, user));
    }

    @PatchMapping("/rooms/{roomId}/messages/{messageId}")
    public ResponseEntity<ChatMessageResponse> editMessage(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @Valid @RequestBody EditMessageRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.editMessage(roomId, messageId, request, user));
    }

    @PostMapping("/rooms/{roomId}/messages/{messageId}/reactions")
    public ResponseEntity<ChatMessageResponse> toggleReaction(
            @PathVariable String roomId,
            @PathVariable String messageId,
            @Valid @RequestBody MessageReactionRequest request,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.toggleReaction(roomId, messageId, request, user));
    }

    @GetMapping("/messages/search")
    public ResponseEntity<List<ChatMessageResponse>> searchMessages(
            @RequestParam String query,
            @AuthenticationPrincipal AuthUser user
    ) {
        return ResponseEntity.ok(chatService.searchMessages(query, user));
    }
}
