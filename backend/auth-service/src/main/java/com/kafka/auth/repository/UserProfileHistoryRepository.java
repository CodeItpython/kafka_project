package com.kafka.auth.repository;

import com.kafka.auth.model.UserProfileHistory;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileHistoryRepository extends JpaRepository<UserProfileHistory, Long> {
    List<UserProfileHistory> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
