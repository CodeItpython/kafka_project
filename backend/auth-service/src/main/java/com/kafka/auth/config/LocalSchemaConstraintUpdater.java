package com.kafka.auth.config;

import java.sql.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
@RequiredArgsConstructor
public class LocalSchemaConstraintUpdater implements ApplicationRunner {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            String databaseName;
            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                databaseName = connection.getMetaData().getDatabaseProductName();
            }
            if (!"PostgreSQL".equalsIgnoreCase(databaseName)) {
                return;
            }

            jdbcTemplate.execute("alter table users drop constraint if exists users_provider_check");
            jdbcTemplate.execute("""
                    alter table users
                    add constraint users_provider_check
                    check (provider in ('LOCAL', 'EMAIL', 'KAKAO'))
                    """);
            log.info("Updated users_provider_check constraint for local auth providers.");
        } catch (Exception exception) {
            log.warn("Could not update users_provider_check constraint. Continuing startup.", exception);
        }
    }
}
