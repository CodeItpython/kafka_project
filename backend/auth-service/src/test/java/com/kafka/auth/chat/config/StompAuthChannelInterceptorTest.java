package com.kafka.auth.chat.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.kafka.auth.chat.service.ChatRoomAccessService;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {
    private static final String EMAIL = "user@example.com";
    private static final String TOKEN = "valid-token";

    @Mock private JwtService jwtService;
    @Mock private UserAccountRepository userAccountRepository;
    @Mock private ChatRoomAccessService chatRoomAccessService;

    private StompAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, userAccountRepository, chatRoomAccessService);
    }

    @Test
    void connectWithoutTokenIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(connect(null), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithInvalidTokenIsRejected() {
        when(jwtService.validateAndGetSubject("bad")).thenThrow(new IllegalArgumentException("nope"));
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer bad"), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectForMissingUserIsRejected() {
        when(jwtService.validateAndGetSubject(TOKEN)).thenReturn(EMAIL);
        when(userAccountRepository.existsByEmail(EMAIL)).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(connect("Bearer " + TOKEN), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectWithValidTokenSetsUser() {
        when(jwtService.validateAndGetSubject(TOKEN)).thenReturn(EMAIL);
        when(userAccountRepository.existsByEmail(EMAIL)).thenReturn(true);

        Message<?> result = interceptor.preSend(connect("Bearer " + TOKEN), null);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(EMAIL);
    }

    @Test
    void subscribeToAccessibleRoomIsAllowed() {
        when(chatRoomAccessService.canAccessRoom("room-1", EMAIL)).thenReturn(true);
        interceptor.preSend(subscribe("/topic/rooms/room-1", EMAIL), null);
        interceptor.preSend(subscribe("/topic/rooms/room-1/read-receipts", EMAIL), null);
    }

    @Test
    void subscribeToForbiddenRoomIsRejected() {
        when(chatRoomAccessService.canAccessRoom("room-9", EMAIL)).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/rooms/room-9", EMAIL), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeToOwnNotificationTopicIsAllowed() {
        interceptor.preSend(subscribe("/topic/notifications/" + sha256(EMAIL), EMAIL), null);
    }

    @Test
    void subscribeToOtherNotificationTopicIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(
                subscribe("/topic/notifications/" + sha256("someone-else@example.com"), EMAIL), null))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void subscribeWithoutAuthenticatedUserIsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/rooms/room-1", null), null))
                .isInstanceOf(MessagingException.class);
    }

    private Message<byte[]> connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> subscribe(String destination, String email) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (email != null) {
            accessor.setUser(() -> email);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
