package com.kafka.auth;

import com.kafka.auth.outbox.OutboxRelayProperties;
import com.kafka.auth.email.EmailVerificationProperties;
import com.kafka.auth.notification.FcmProperties;
import org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableDiscoveryClient
@EnableConfigurationProperties({OutboxRelayProperties.class, FcmProperties.class, EmailVerificationProperties.class})
@SpringBootApplication(exclude = ReactiveElasticsearchRepositoriesAutoConfiguration.class)
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
