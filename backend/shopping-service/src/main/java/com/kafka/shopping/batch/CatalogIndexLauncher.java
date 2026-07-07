package com.kafka.shopping.batch;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * @Async로 호출 스레드(기동/스케줄러)를 막지 않고, 동시 실행 가드로 Naver 호출이 겹쳐 중복되는 것을 막는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogIndexLauncher {
    private final JobLauncher jobLauncher;
    private final Job catalogIndexJob;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Async
    public void launch(String trigger) {
        // 실행마다 다른 runAt으로 새 JobInstance가 만들어져 Batch 자체의 중복 실행 가드가 없으므로,
        // 겹침(기동+스케줄, 혹은 30분보다 오래 걸리는 실행)이 Naver 호출을 두 배로 쓰지 않도록 직접 가드.
        if (!running.compareAndSet(false, true)) {
            log.info("Catalog index job already running; skipping {} trigger.", trigger);
            return;
        }
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("runAt", System.currentTimeMillis())
                    .addString("trigger", trigger)
                    .toJobParameters();
            var execution = jobLauncher.run(catalogIndexJob, params);
            long written = execution.getStepExecutions().stream().mapToLong(step -> step.getWriteCount()).sum();
            long skipped = execution.getStepExecutions().stream().mapToLong(step -> step.getSkipCount()).sum();
            log.info("Catalog index job done: status={}, written={}, skipped={} (trigger={})",
                    execution.getStatus(), written, skipped, trigger);
        } catch (Exception exception) {
            log.warn("Catalog index job launch failed (trigger={}): {}", trigger, exception.getMessage());
        } finally {
            running.set(false);
        }
    }

    // @Async를 여기에도 둬야 스케줄러가 프록시를 통해 호출할 때 별도 스레드에서 실행된다(self-invocation
    // 으로 launch()의 @Async가 우회되어 스케줄러 스레드를 블로킹하던 문제 해소).
    @Async
    @Scheduled(
            initialDelayString = "${app.search.warm-interval-minutes:30}",
            fixedDelayString = "${app.search.warm-interval-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public void scheduled() {
        launch("scheduled");
    }
}
