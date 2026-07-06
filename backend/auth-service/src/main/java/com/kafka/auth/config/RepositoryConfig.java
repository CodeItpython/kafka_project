package com.kafka.auth.config;

import com.kafka.auth.admin.audit.AdminAuditEventRepository;
import com.kafka.auth.repository.EmailVerificationCodeRepository;
import com.kafka.auth.repository.UserAccountRepository;
import com.kafka.auth.repository.UserProfileHistoryRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * auth-service is relational-only after chat moved out: users, email codes,
 * profile history and admin audit. No MongoDB/Elasticsearch repositories remain.
 */
@Configuration
@EnableJpaRepositories(
        basePackageClasses = {
                UserAccountRepository.class,
                EmailVerificationCodeRepository.class,
                UserProfileHistoryRepository.class,
                AdminAuditEventRepository.class
        }
)
public class RepositoryConfig {
}
