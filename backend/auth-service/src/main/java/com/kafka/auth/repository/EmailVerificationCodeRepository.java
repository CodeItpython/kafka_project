package com.example.kafka.auth.repository;

import com.example.kafka.auth.model.EmailVerificationCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCode, Long> {
    Optional<EmailVerificationCode> findTopByEmailAndCodeAndUsedFalseOrderByExpiresAtDesc(String email, String code);
}
