package com.kafka.chat.config;

import com.kafka.chat.notification.PushDeviceTokenRepository;
import com.kafka.chat.notification.UserNotificationRepository;
import com.kafka.chat.outbox.OutboxEventRepository;
import com.kafka.chat.repository.ChatMessageDeliveryStateRepository;
import com.kafka.chat.repository.ChatMessageRepository;
import com.kafka.chat.repository.ChatRoomReadStateRepository;
import com.kafka.chat.repository.ChatRoomRepository;
import com.kafka.chat.repository.ChatRoomUserPreferenceRepository;
import com.kafka.chat.repository.GameScoreRepository;
import com.kafka.chat.search.ChatMessageSearchRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * chat-service persists across three stores that share the {@code com.kafka.chat}
 * base package, so the JPA/Mongo/Elasticsearch repository scanners are given
 * explicit base classes and exclude filters to avoid claiming each other's
 * repositories. ChatMessageDocument history lives in MongoDB; rooms/read-states/
 * delivery/outbox/notifications are relational; the search index is Elasticsearch.
 */
@Configuration
@EnableJpaRepositories(
        basePackageClasses = {
                ChatMessageDeliveryStateRepository.class,
                ChatRoomRepository.class,
                ChatRoomReadStateRepository.class,
                ChatRoomUserPreferenceRepository.class,
                GameScoreRepository.class,
                OutboxEventRepository.class,
                UserNotificationRepository.class,
                PushDeviceTokenRepository.class
        },
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ChatMessageRepository.class)
)
@EnableMongoRepositories(
        basePackageClasses = ChatMessageRepository.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        ChatRoomRepository.class,
                        ChatMessageDeliveryStateRepository.class,
                        ChatRoomReadStateRepository.class,
                        ChatRoomUserPreferenceRepository.class,
                        GameScoreRepository.class,
                        OutboxEventRepository.class,
                        UserNotificationRepository.class,
                        PushDeviceTokenRepository.class
                }
        )
)
@EnableElasticsearchRepositories(basePackageClasses = ChatMessageSearchRepository.class)
public class RepositoryConfig {
}
