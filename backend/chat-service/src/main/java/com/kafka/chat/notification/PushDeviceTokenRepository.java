package com.kafka.chat.notification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, Long> {
    Optional<PushDeviceToken> findByTokenHash(String tokenHash);

    List<PushDeviceToken> findByUserEmailInAndEnabledTrue(Collection<String> userEmails);

    List<PushDeviceToken> findByUserEmailAndEnabledTrue(String userEmail);
}
