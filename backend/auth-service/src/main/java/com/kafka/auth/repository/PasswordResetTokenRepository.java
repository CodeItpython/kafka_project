package com.kafka.auth.repository;

import com.kafka.auth.model.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findTopByTokenAndUsedFalseOrderByExpiresAtDesc(String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasswordResetToken token
            set token.used = true
            where lower(token.email) = lower(:email)
              and token.used = false
            """)
    int markUnusedTokensAsUsed(@Param("email") String email);
}
