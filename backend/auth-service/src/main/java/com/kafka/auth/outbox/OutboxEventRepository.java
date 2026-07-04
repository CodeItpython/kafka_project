package com.kafka.auth.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    /**
     * Unlocked read of the candidate event ids. Each id is then claimed and
     * processed in its own short transaction via {@link #lockForRelay(String)}.
     */
    @Query("""
            select event.id
            from OutboxEvent event
            where event.status in :statuses
              and event.nextAttemptAt <= :now
            order by event.createdAt asc
            """)
    List<String> findReadyEventIds(
            @Param("statuses") Collection<OutboxEventStatus> statuses,
            @Param("now") Instant now,
            Pageable pageable
    );

    /**
     * Locks a single row FOR UPDATE SKIP LOCKED so multiple relay workers can
     * process disjoint events concurrently instead of blocking on each other.
     * The special lock-timeout value {@code -2} maps to SKIP LOCKED in Hibernate.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select event from OutboxEvent event where event.id = :id")
    Optional<OutboxEvent> lockForRelay(@Param("id") String id);
}
