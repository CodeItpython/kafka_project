package com.kafka.chat.notification;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    List<UserNotification> findByRecipientEmailOrderByCreatedAtDesc(String recipientEmail, Pageable pageable);

    long countByRecipientEmailAndReadAtIsNull(String recipientEmail);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotification notification
            set notification.readAt = current_timestamp
            where notification.recipientEmail = :recipientEmail
              and notification.readAt is null
            """)
    int markAllRead(@Param("recipientEmail") String recipientEmail);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update UserNotification notification
            set notification.readAt = current_timestamp
            where notification.recipientEmail = :recipientEmail
              and notification.id in :ids
              and notification.readAt is null
            """)
    int markRead(@Param("recipientEmail") String recipientEmail, @Param("ids") Collection<Long> ids);
}
