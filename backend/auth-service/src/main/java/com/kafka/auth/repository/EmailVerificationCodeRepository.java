package com.kafka.auth.repository;

import com.kafka.auth.model.EmailVerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    Optional<EmailVerificationCode> findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(String email, String code);
}
