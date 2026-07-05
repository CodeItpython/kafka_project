package com.kafka.chat.outbox;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelayScheduler {
    private final OutboxRelayService outboxRelayService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(
            initialDelayString = "${app.outbox.relay.initial-delay-ms:1000}",
            fixedDelayString = "${app.outbox.relay.fixed-delay-ms:1000}"
    )
    public void publishPendingEvents() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<String> readyIds = outboxRelayService.findReadyEventIds();
            int published = 0;
            for (String id : readyIds) {
                try {
                    if (outboxRelayService.publishOne(id)) {
                        published++;
                    }
                } catch (RuntimeException exception) {
                    // Isolate per-event failures so one bad event never stops the batch.
                    log.warn("Failed to relay outbox event. id={}", id, exception);
                }
            }
            if (published > 0) {
                log.debug("Processed outbox events. count={}", published);
            }
        } finally {
            running.set(false);
        }
    }
}
