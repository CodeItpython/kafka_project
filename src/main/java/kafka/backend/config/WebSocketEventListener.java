package kafka.backend.config;

import kafka.backend.model.ChatMessage;
import kafka.backend.model.User;
import kafka.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private UserRepository userRepository;

    private Set<String> onlineUsers = new HashSet<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = event.getUser() != null ? event.getUser().getName() : null;

        if (username != null) {
            onlineUsers.add(username);
            logger.info("User connected: " + username);

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(ChatMessage.MessageType.JOIN);
            chatMessage.setSender(username);
            chatMessage.setTimestamp(LocalDateTime.now());
            messagingTemplate.convertAndSend("/topic/public", chatMessage);

            // Broadcast online users list
            broadcastOnlineUsers();
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String username = event.getUser() != null ? event.getUser().getName() : null;
        if(username != null) {
            onlineUsers.remove(username);
            logger.info("User Disconnected : " + username);

            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setType(ChatMessage.MessageType.LEAVE);
            chatMessage.setSender(username);
            chatMessage.setTimestamp(LocalDateTime.now());

            messagingTemplate.convertAndSend("/topic/public", chatMessage);

            // Broadcast online users list
            broadcastOnlineUsers();
        }
    }

    private void broadcastOnlineUsers() {
        // In a real application, you might fetch user details from DB
        // For now, just send usernames
        messagingTemplate.convertAndSend("/topic/users", onlineUsers);
    }
}
