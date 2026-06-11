package com.example.kafka.auth.repository;

import com.example.kafka.auth.model.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserAccount> findTop30ByOrderByNameAsc();

    List<UserAccount> findTop30ByEmailContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByNameAsc(String email, String name);
}
