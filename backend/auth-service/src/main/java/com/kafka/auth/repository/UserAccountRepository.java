package com.kafka.auth.repository;

import com.kafka.auth.model.UserAccount;
import com.kafka.auth.model.AuthProvider;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findByProviderAndProviderId(AuthProvider provider, String providerId);

    boolean existsByEmail(String email);

    List<UserAccount> findTop30ByOrderByNameAsc();

    List<UserAccount> findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(String email, String name);

    List<UserAccount> findByEmailIn(Collection<String> emails);
}
