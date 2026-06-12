package com.kafka.auth.config;

import com.kafka.auth.model.AuthProvider;
import com.kafka.auth.model.UserAccount;
import com.kafka.auth.repository.UserAccountRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DevDataInitializer implements ApplicationRunner {
    private static final String DEFAULT_PASSWORD = "password123";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean seedUsers;

    public DevDataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.dev.seed-users:true}") boolean seedUsers
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedUsers = seedUsers;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!seedUsers) {
            return;
        }
        List<SeedUser> users = List.of(
                new SeedUser("user@example.com", "건우"),
                new SeedUser("minji@example.com", "민지"),
                new SeedUser("junho@example.com", "준호"),
                new SeedUser("seoyeon@example.com", "서연"),
                new SeedUser("hyejin@example.com", "혜진")
        );
        users.stream()
                .filter(user -> !userAccountRepository.existsByEmail(user.email()))
                .map(user -> new UserAccount(user.email(), user.name(), passwordEncoder.encode(DEFAULT_PASSWORD), AuthProvider.LOCAL))
                .forEach(userAccountRepository::save);
        log.info("Development test users are ready. Password is {}.", DEFAULT_PASSWORD);
    }

    private record SeedUser(String email, String name) {
    }
}
