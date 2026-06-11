package com.example.kafka.auth.config;

import com.example.kafka.auth.model.AuthProvider;
import com.example.kafka.auth.model.UserAccount;
import com.example.kafka.auth.repository.UserAccountRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DevDataInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DevDataInitializer.class);
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
