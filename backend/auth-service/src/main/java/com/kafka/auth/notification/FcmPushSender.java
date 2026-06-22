package com.kafka.auth.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class FcmPushSender {
    private static final String APP_NAME = "kafka-talk-fcm";

    private final FcmProperties properties;
    private volatile FirebaseMessaging messaging;

    @PostConstruct
    void initialize() {
        if (!properties.enabled()) {
            log.info("FCM push sender is disabled.");
            return;
        }
        try {
            GoogleCredentials credentials = credentials();
            if (credentials == null) {
                log.warn("FCM push sender is enabled but service account is not configured.");
                return;
            }
            FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
            if (StringUtils.hasText(properties.projectId())) {
                builder.setProjectId(properties.projectId());
            }
            FirebaseApp app = FirebaseApp.getApps().stream()
                    .filter(existing -> APP_NAME.equals(existing.getName()))
                    .findFirst()
                    .orElseGet(() -> FirebaseApp.initializeApp(builder.build(), APP_NAME));
            messaging = FirebaseMessaging.getInstance(app);
            log.info("FCM push sender initialized. dryRun={}", properties.dryRun());
        } catch (RuntimeException | IOException exception) {
            log.warn("Unable to initialize FCM push sender. Push delivery will be skipped.", exception);
        }
    }

    public void send(UserNotification notification, List<PushDeviceToken> tokens) {
        FirebaseMessaging currentMessaging = messaging;
        if (currentMessaging == null || tokens.isEmpty()) {
            return;
        }
        tokens.forEach(token -> send(notification, token, currentMessaging));
    }

    private void send(UserNotification notification, PushDeviceToken token, FirebaseMessaging currentMessaging) {
        try {
            Message message = Message.builder()
                    .setToken(token.getToken())
                    .setNotification(com.google.firebase.messaging.Notification.builder()
                            .setTitle(notification.getTitle())
                            .setBody(notification.getBody())
                            .build())
                    .putAllData(Map.of(
                            "type", notification.getType().name(),
                            "notificationId", String.valueOf(notification.getId()),
                            "roomId", valueOrEmpty(notification.getTargetRoomId()),
                            "messageId", valueOrEmpty(notification.getTargetMessageId())
                    ))
                    .setWebpushConfig(webpushConfig())
                    .build();
            String messageId = currentMessaging.send(message, properties.dryRun());
            log.debug("FCM push sent. notificationId={}, tokenId={}, messageId={}", notification.getId(), token.getId(), messageId);
        } catch (FirebaseMessagingException exception) {
            log.warn("Unable to send FCM push. notificationId={}, tokenId={}", notification.getId(), token.getId(), exception);
        }
    }

    private WebpushConfig webpushConfig() {
        WebpushFcmOptions.Builder options = WebpushFcmOptions.builder();
        if (StringUtils.hasText(properties.webPushLink())) {
            options.setLink(properties.webPushLink());
        }
        WebpushConfig.Builder builder = WebpushConfig.builder().setFcmOptions(options.build());
        if (StringUtils.hasText(properties.webPushIcon())) {
            builder.setNotification(WebpushNotification.builder()
                    .setIcon(properties.webPushIcon())
                    .build());
        }
        return builder.build();
    }

    private GoogleCredentials credentials() throws IOException {
        if (StringUtils.hasText(properties.serviceAccountJson())) {
            return GoogleCredentials.fromStream(new ByteArrayInputStream(properties.serviceAccountJson().getBytes(StandardCharsets.UTF_8)));
        }
        if (StringUtils.hasText(properties.serviceAccountPath())) {
            try (FileInputStream inputStream = new FileInputStream(properties.serviceAccountPath())) {
                return GoogleCredentials.fromStream(inputStream);
            }
        }
        return null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
