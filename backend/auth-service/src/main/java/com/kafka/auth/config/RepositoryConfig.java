package com.kafka.auth.config;

import com.kafka.auth.chat.repository.ChatMessageRepository;
import com.kafka.auth.chat.repository.ChatMessageDeliveryStateRepository;
import com.kafka.auth.chat.repository.ChatRoomReadStateRepository;
import com.kafka.auth.chat.repository.ChatRoomRepository;
import com.kafka.auth.chat.repository.ChatRoomUserPreferenceRepository;
import com.kafka.auth.chat.search.ChatMessageSearchRepository;
import com.kafka.auth.notification.PushDeviceTokenRepository;
import com.kafka.auth.notification.UserNotificationRepository;
import com.kafka.auth.outbox.OutboxEventRepository;
import com.kafka.auth.repository.EmailVerificationCodeRepository;
import com.kafka.auth.repository.UserAccountRepository;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.context.annotation.FilterType;

@Configuration
@EnableJpaRepositories(
        basePackageClasses = {
                UserAccountRepository.class,
                EmailVerificationCodeRepository.class,
                ChatMessageDeliveryStateRepository.class,
                ChatRoomRepository.class,
                ChatRoomReadStateRepository.class,
                ChatRoomUserPreferenceRepository.class,
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
                        UserAccountRepository.class,
                        EmailVerificationCodeRepository.class,
                        OutboxEventRepository.class,
                        UserNotificationRepository.class,
                        PushDeviceTokenRepository.class
                }
        )
)
@EnableElasticsearchRepositories(basePackageClasses = ChatMessageSearchRepository.class)
public class RepositoryConfig {
}
