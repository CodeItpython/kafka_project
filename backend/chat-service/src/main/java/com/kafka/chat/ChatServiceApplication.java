package com.kafka.chat;

import com.kafka.chat.notification.FcmProperties;
import com.kafka.chat.outbox.OutboxRelayProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableFeignClients
@EnableConfigurationProperties({OutboxRelayProperties.class, FcmProperties.class})
@SpringBootApplication(exclude = ReactiveElasticsearchRepositoriesAutoConfiguration.class)
public class ChatServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
