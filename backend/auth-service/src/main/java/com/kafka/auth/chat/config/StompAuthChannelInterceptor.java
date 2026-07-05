package com.kafka.auth.chat.config;

import com.kafka.auth.chat.service.ChatRoomAccessService;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authenticates the STOMP CONNECT frame with the same JWT used for REST, and
 * authorizes SUBSCRIBE frames so a user can only listen to rooms they can access
 * and to their own notification topic. Without this, the /ws handshake is open
 * and any client could subscribe to another room's /topic and eavesdrop.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    private static final String NOTIFICATION_TOPIC_PREFIX = "/topic/notifications/";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final ChatRoomAccessService chatRoomAccessService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Use the mutable accessor tied to this message (not wrap(), which copies)
        // so setUser() on CONNECT propagates to the session for later frames.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String email = authenticate(accessor);
            accessor.setUser(() -> email);
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private String authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("WebSocket 연결에 인증 토큰이 필요합니다.");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        String email;
        try {
            email = jwtService.validateAndGetSubject(token);
        } catch (RuntimeException exception) {
            throw new MessagingException("WebSocket 인증 토큰이 유효하지 않습니다.");
        }
        if (!userAccountRepository.existsByEmail(email)) {
            throw new MessagingException("존재하지 않는 사용자입니다.");
        }
        return email;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String email = accessor.getUser() == null ? null : accessor.getUser().getName();
        if (email == null || email.isBlank()) {
            throw new MessagingException("인증되지 않은 구독 요청입니다.");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("구독 대상이 없습니다.");
        }
        if (destination.startsWith(ROOM_TOPIC_PREFIX)) {
            String roomId = destination.substring(ROOM_TOPIC_PREFIX.length()).split("/", 2)[0];
            if (!chatRoomAccessService.canAccessRoom(roomId, email)) {
                throw new MessagingException("이 채팅방을 구독할 권한이 없습니다.");
            }
            return;
        }
        if (destination.startsWith(NOTIFICATION_TOPIC_PREFIX)) {
            if (!destination.equals(notificationTopicFor(email))) {
                throw new MessagingException("이 알림 채널을 구독할 권한이 없습니다.");
            }
        }
        // Other destinations (none sensitive in this app) are left untouched.
    }

    /** Mirrors NotificationService's topic scheme: /topic/notifications/{sha256(lowercased email)}. */
    private String notificationTopicFor(String email) {
        return NOTIFICATION_TOPIC_PREFIX + sha256(email.toLowerCase(Locale.ROOT));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 해시를 사용할 수 없습니다.", exception);
        }
    }
}
