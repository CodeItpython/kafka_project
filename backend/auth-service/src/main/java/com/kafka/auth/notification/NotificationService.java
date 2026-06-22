package com.kafka.auth.notification;

import com.kafka.auth.chat.dto.ChatMessageEvent;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.notification.NotificationDtos.NotificationListResponse;
import com.kafka.auth.notification.NotificationDtos.NotificationResponse;
import com.kafka.auth.notification.NotificationDtos.NotificationSubscriptionResponse;
import com.kafka.auth.notification.NotificationDtos.RegisterPushTokenRequest;
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
                        event.roomName(),
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
    public NotificationListResponse notifications(UserAccount user) {
        List<NotificationResponse> notifications = userNotificationRepository
                .findByRecipientEmailOrderByCreatedAtDesc(user.getEmail(), PageRequest.of(0, DEFAULT_NOTIFICATION_LIMIT))
                .stream()
                .map(this::toResponse)
                .toList();
        return new NotificationListResponse(notifications, unreadCount(user));
    }

    @Transactional(readOnly = true)
    public NotificationSubscriptionResponse subscription(UserAccount user) {
        return new NotificationSubscriptionResponse(topicFor(user.getEmail()), unreadCount(user));
    }

    @Transactional
    public NotificationListResponse markRead(UserAccount user, Collection<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            userNotificationRepository.markRead(user.getEmail(), ids);
        }
        return notifications(user);
    }

    @Transactional
    public NotificationListResponse markAllRead(UserAccount user) {
        userNotificationRepository.markAllRead(user.getEmail());
        return notifications(user);
    }

    @Transactional
    public void registerPushToken(RegisterPushTokenRequest request, UserAccount user) {
        String token = request.token().trim();
        String tokenHash = sha256(token);
        PushDeviceToken deviceToken = pushDeviceTokenRepository.findByTokenHash(tokenHash)
                .orElseGet(() -> new PushDeviceToken(user.getEmail(), tokenHash, token, request.platform()));
        deviceToken.refresh(user.getEmail(), token, request.platform());
        pushDeviceTokenRepository.save(deviceToken);
    }

    @Transactional
    public void unregisterPushToken(String token, UserAccount user) {
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

    private long unreadCount(UserAccount user) {
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
