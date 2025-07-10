package kafka.backend.controller;

import kafka.backend.model.ChatMessage;
import kafka.backend.service.ChatMessageService;
import kafka.backend.service.FileStorageService;
import kafka.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
public class ChatController {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private UserService userService;

    @Autowired
    private FileStorageService fileStorageService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        System.out.println("Received chat message from senderId: " + chatMessage.getSenderId());
        // Get sender username from senderId
        userService.findById(chatMessage.getSenderId()).ifPresentOrElse(user -> {
            chatMessage.setSender(user.getUsername());
            System.out.println("Found user with username: " + user.getUsername());
        }, () -> {
            System.err.println("User not found for senderId: " + chatMessage.getSenderId());
        });

        // Set message type
        if (chatMessage.getType() == null) {
            chatMessage.setType(ChatMessage.MessageType.CHAT);
        }

        // Save message to database
        chatMessage.setTimestamp(LocalDateTime.now());
        ChatMessage savedMessage = chatMessageService.saveMessage(chatMessage);

        // Send message to receiver
        messagingTemplate.convertAndSendToUser(
                String.valueOf(chatMessage.getReceiverId()), "/queue/messages", savedMessage);
        // Also send to sender's own queue to display sent message
        messagingTemplate.convertAndSendToUser(
                String.valueOf(chatMessage.getSenderId()), "/queue/messages", savedMessage);

        // Send notification to receiver's friend list
        messagingTemplate.convertAndSendToUser(
                String.valueOf(chatMessage.getReceiverId()), "/queue/notifications", savedMessage);
    }

    @PostMapping("/upload-image")
    public void uploadImage(@RequestParam("senderId") Long senderId,
                            @RequestParam("receiverId") Long receiverId,
                            @RequestParam("file") MultipartFile file) throws IOException {
        String imageUrl = fileStorageService.storeFile(file);

        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSenderId(senderId);
        chatMessage.setReceiverId(receiverId);
        chatMessage.setType(ChatMessage.MessageType.IMAGE);
        chatMessage.setImageUrl(imageUrl);
        chatMessage.setContent("[Image]");

        userService.findById(senderId).ifPresent(user -> chatMessage.setSender(user.getUsername()));

        chatMessage.setTimestamp(LocalDateTime.now());
        ChatMessage savedMessage = chatMessageService.saveMessage(chatMessage);

        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiverId), "/queue/messages", savedMessage);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(senderId), "/queue/messages", savedMessage);
        messagingTemplate.convertAndSendToUser(
                String.valueOf(receiverId), "/queue/notifications", savedMessage);
    }

    @MessageMapping("/chat.getHistory")
    public void getChatHistory(@Payload ChatMessage chatMessage, SimpMessageHeaderAccessor headerAccessor) {
        Long user1Id = chatMessage.getSenderId();
        Long user2Id = chatMessage.getReceiverId();
        List<ChatMessage> chatHistory = chatMessageService.getChatHistory(user1Id, user2Id);

        // Send history back to the requesting user
        messagingTemplate.convertAndSendToUser(
                String.valueOf(user1Id), "/queue/history", chatHistory);
    }
}
