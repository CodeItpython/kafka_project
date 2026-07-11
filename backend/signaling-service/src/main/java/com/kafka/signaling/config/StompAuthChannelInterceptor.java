package com.kafka.signaling.config;

import com.kafka.signaling.security.JwtService;
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
 * Authenticates the STOMP CONNECT frame with the same JWT used for REST/chat, and
 * pins the session's Principal to the caller's (lowercased) email. That Principal
 * is what {@code SimpMessagingTemplate.convertAndSendToUser} routes on, so a peer
 * only ever receives signals addressed to its own {@code /user/queue/call}.
 *
 * <p>SUBSCRIBE is restricted to {@code /user/**} destinations. Those are rewritten
 * per-session by Spring's user-destination handler, so a client only ever receives
 * its own signals. Raw broker destinations ({@code /queue/**}, {@code /topic/**})
 * are rejected — otherwise a wildcard subscription like {@code /queue/**} would
 * match the concrete {@code /queue/call-user{session}} names and eavesdrop on every
 * call on the instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        // Use the mutable accessor tied to this message (not wrap(), which copies)
        // so setUser() on CONNECT propagates to the session for later SEND frames.
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String email = authenticate(accessor);
            accessor.setUser(() -> email);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            authorizeSubscribe(accessor);
        }
        return message;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new MessagingException("인증되지 않은 구독 요청입니다.");
        }
        String destination = accessor.getDestination();
        // Only user-destinations are allowed; they are rewritten per-session so a
        // client can never receive another user's signals. Everything else (raw
        // /queue, /topic, wildcards) is refused to prevent broker-level eavesdropping.
        if (destination == null || !destination.startsWith("/user/")) {
            throw new MessagingException("허용되지 않은 구독 대상입니다.");
        }
    }

    private String authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("영상통화 연결에 인증 토큰이 필요합니다.");
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            // Lowercase so caller-supplied toEmail matches the callee's Principal
            // regardless of the case each side stored the address in.
            return jwtService.validateAndGetSubject(token).toLowerCase(Locale.ROOT);
        } catch (RuntimeException exception) {
            throw new MessagingException("영상통화 인증 토큰이 유효하지 않습니다.");
        }
    }
}
