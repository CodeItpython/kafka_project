package com.kafka.auth.outbox;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from OutboxEvent event
            where event.status in :statuses
              and event.nextAttemptAt <= :now
            order by event.createdAt asc
            """)
    List<OutboxEvent> findReadyEvents(
            @Param("statuses") Collection<OutboxEventStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );
}
