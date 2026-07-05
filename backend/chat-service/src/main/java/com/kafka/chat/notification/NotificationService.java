package com.kafka.chat.notification;

import com.kafka.chat.dto.ChatMessageEvent;
import com.kafka.chat.model.ChatRoomType;
import com.kafka.chat.repository.ChatRoomRepository;
import com.kafka.chat.security.AuthUser;
import com.kafka.chat.notification.NotificationDtos.NotificationListResponse;
import com.kafka.chat.notification.NotificationDtos.NotificationResponse;
import com.kafka.chat.notification.NotificationDtos.NotificationSubscriptionResponse;
import com.kafka.chat.notification.NotificationDtos.RegisterPushTokenRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private static final int DEFAULT_NOTIFICATION_LIMIT = 30;

    private final UserNotificationRepository userNotificationRepository;
    private final PushDeviceTokenRepository pushDeviceTokenRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FcmPushSender fcmPushSender;

    @Transactional
    public void createChatMessageNotifications(ChatMessageEvent event, Set<String> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        List<PushDeviceToken> tokens = pushDeviceTokenRepository.findByUserEmailInAndEnabledTrue(recipients);
        recipients.stream()
                .map(recipient -> userNotificationRepository.save(new UserNotification(
                        recipient,
                        event.senderEmail(),
                        event.senderName(),
                        NotificationType.CHAT_MESSAGE,
                        notificationTitle(event),
                        previewBody(event),
                        event.roomId(),
                        event.messageId()
                )))
                .forEach(notification -> {
                    publishRealtime(notification);
                    fcmPushSender.send(notification, tokens.stream()
                            .filter(token -> token.getUserEmail().equalsIgnoreCase(notification.getRecipientEmail()))
                            .toList());
                });
    }

    @Transactional(readOnly = true)
    public NotificationListResponse notifications(AuthUser user) {
        List<NotificationResponse> notifications = userNotificationRepository
                .findByRecipientEmailOrderByCreatedAtDesc(user.getEmail(), PageRequest.of(0, DEFAULT_NOTIFICATION_LIMIT))
                .stream()
                .map(this::toResponse)
                .toList();
        return new NotificationListResponse(notifications, unreadCount(user));
    }

    @Transactional(readOnly = true)
    public NotificationSubscriptionResponse subscription(AuthUser user) {
        return new NotificationSubscriptionResponse(topicFor(user.getEmail()), unreadCount(user));
    }

    @Transactional
    public NotificationListResponse markRead(AuthUser user, Collection<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            userNotificationRepository.markRead(user.getEmail(), ids);
        }
        return notifications(user);
    }

    @Transactional
    public NotificationListResponse markAllRead(AuthUser user) {
        userNotificationRepository.markAllRead(user.getEmail());
        return notifications(user);
    }

    @Transactional
    public void registerPushToken(RegisterPushTokenRequest request, AuthUser user) {
        String token = request.token().trim();
        String tokenHash = sha256(token);
        PushDeviceToken deviceToken = pushDeviceTokenRepository.findByTokenHash(tokenHash)
                .orElseGet(() -> new PushDeviceToken(user.getEmail(), tokenHash, token, request.platform()));
        deviceToken.refresh(user.getEmail(), token, request.platform());
        pushDeviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void unregisterPushToken(String token, AuthUser user) {
        String tokenHash = sha256(token.trim());
        pushDeviceTokenRepository.findByTokenHash(tokenHash)
                .filter(deviceToken -> deviceToken.getUserEmail().equalsIgnoreCase(user.getEmail()))
                .ifPresent(deviceToken -> {
                    deviceToken.disable();
                    pushDeviceTokenRepository.save(deviceToken);
                });
    }

    private void publishRealtime(UserNotification notification) {
        try {
            messagingTemplate.convertAndSend(topicFor(notification.getRecipientEmail()), toResponse(notification));
        } catch (RuntimeException exception) {
            log.debug("Unable to publish realtime notification. notificationId={}", notification.getId(), exception);
        }
    }

    private long unreadCount(AuthUser user) {
        return userNotificationRepository.countByRecipientEmailAndReadAtIsNull(user.getEmail());
    }

    private NotificationResponse toResponse(UserNotification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getActorEmail(),
                notification.getActorName(),
                notification.getTargetRoomId(),
                notification.getTargetMessageId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }

    private String topicFor(String email) {
        return "/topic/notifications/" + sha256(email.toLowerCase());
    }

    private String notificationTitle(ChatMessageEvent event) {
        return chatRoomRepository.findById(event.roomId())
                .filter(room -> room.getType() == ChatRoomType.DIRECT)
                .map(room -> event.senderName())
                .filter(senderName -> senderName != null && !senderName.isBlank())
                .orElse(event.roomName());
    }

    private String previewBody(ChatMessageEvent event) {
        if (event.content() != null && !event.content().isBlank()) {
            String content = event.content().trim();
            return content.length() > 120 ? content.substring(0, 120) + "..." : content;
        }
        if (event.attachmentName() != null && !event.attachmentName().isBlank()) {
            return event.senderName() + "님이 " + event.attachmentName() + " 파일을 보냈습니다.";
        }
        return event.senderName() + "님이 메시지를 보냈습니다.";
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
