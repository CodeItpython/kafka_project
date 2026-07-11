package com.kafka.signaling.signaling;

import java.security.Principal;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Stateless relay for 1:1 call signaling. A client publishes to {@code /app/call/signal}
 * with a target {@code toEmail}; the server stamps the authenticated sender and
 * forwards the signal to that peer's {@code /user/queue/call}. No call state is
 * persisted — the state machine lives entirely on the two clients.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SignalingController {
    private static final String CALL_QUEUE = "/queue/call";

    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/call/signal")
    public void relay(@Payload CallSignal signal, Principal principal) {
        if (principal == null || principal.getName() == null) {
            return;
        }
        if (signal == null || signal.type() == null
                || signal.toEmail() == null || signal.toEmail().isBlank()) {
            return;
        }
        String from = principal.getName();
        String to = signal.toEmail().toLowerCase(Locale.ROOT);
        // Overwrite fromEmail with the authenticated identity so it cannot be spoofed.
        CallSignal out = signal.withFrom(from);
        messagingTemplate.convertAndSendToUser(to, CALL_QUEUE, out);
        log.debug("relayed {} from {} to {}", signal.type(), from, to);
    }
}
