package com.kafka.auth.admin.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, Long> {
    Page<AdminAuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
