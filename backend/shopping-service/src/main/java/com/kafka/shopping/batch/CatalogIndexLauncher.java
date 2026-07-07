package com.kafka.shopping.batch;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 카탈로그 색인 잡을 실행한다(시작 시 1회는 인덱스 초기화기가, 이후는 여기 @Scheduled가 트리거).
 * 매 실행마다 다른 runAt 파라미터로 새 JobInstance를 만든다. @Async라 호출 스레드를 막지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogIndexLauncher {
    private final JobLauncher jobLauncher;
    private final Job catalogIndexJob;

    @Async
    public void launch(String trigger) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .addString("trigger", trigger)
                    .toJobParameters();
            var execution = jobLauncher.run(catalogIndexJob, params);
            long written = execution.getStepExecutions().stream().mapToLong(s -> s.getWriteCount()).sum();
            long skipped = execution.getStepExecutions().stream().mapToLong(s -> s.getSkipCount()).sum();
            log.info("Catalog index job done: status={}, written={}, skipped={} (trigger={})",
                    execution.getStatus(), written, skipped, trigger);
        } catch (Exception exception) {
            log.warn("Catalog index job launch failed (trigger={}): {}", trigger, exception.getMessage());
        }
    }

    @Scheduled(
            initialDelayString = "${app.search.warm-interval-minutes:30}",
            fixedDelayString = "${app.search.warm-interval-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public void scheduled() {
        launch("scheduled");
    }
}
