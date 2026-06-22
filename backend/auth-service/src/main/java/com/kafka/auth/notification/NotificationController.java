package com.kafka.auth.notification;

import com.kafka.auth.model.UserAccount;
import com.kafka.auth.notification.NotificationDtos.NotificationListResponse;
import com.kafka.auth.notification.NotificationDtos.NotificationSubscriptionResponse;
import com.kafka.auth.notification.NotificationDtos.RegisterPushTokenRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationListResponse> notifications(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(notificationService.notifications(user));
    }

    @GetMapping("/subscription")
    public ResponseEntity<NotificationSubscriptionResponse> subscription(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(notificationService.subscription(user));
    }

    @PatchMapping("/read")
    public ResponseEntity<NotificationListResponse> markRead(
            @RequestBody List<Long> notificationIds,
            @AuthenticationPrincipal UserAccount user
    ) {
        return ResponseEntity.ok(notificationService.markRead(user, notificationIds));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<NotificationListResponse> markAllRead(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(notificationService.markAllRead(user));
    }

    @PostMapping("/push-tokens")
    public ResponseEntity<Void> registerPushToken(
            @Valid @RequestBody RegisterPushTokenRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        notificationService.registerPushToken(request, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/push-tokens")
    public ResponseEntity<Void> unregisterPushToken(
            @RequestBody RegisterPushTokenRequest request,
            @AuthenticationPrincipal UserAccount user
    ) {
        notificationService.unregisterPushToken(request.token(), user);
        return ResponseEntity.noContent().build();
    }
}
