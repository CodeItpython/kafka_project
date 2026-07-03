package com.kafka.auth.repository;

import com.kafka.auth.model.EmailVerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    Optional<EmailVerificationCode> findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(String email, String code);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update EmailVerificationCode code
            set code.used = true
            where lower(code.email) = lower(:email)
              and code.used = false
            """)
    int markUnusedCodesAsUsed(@Param("email") String email);
}
