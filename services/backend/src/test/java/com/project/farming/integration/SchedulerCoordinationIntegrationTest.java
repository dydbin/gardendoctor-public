package com.project.farming.integration;

import com.project.farming.global.scheduling.MySqlAdvisoryLockService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "integration"})
class SchedulerCoordinationIntegrationTest {

    @Autowired
    private MySqlAdvisoryLockService advisoryLockService;

    @Test
    void mysqlLockShouldAllowOnlyOneConcurrentInstanceAndReleaseAfterCompletion() throws Exception {
        String lockName = "gd:test:" + UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();

        try {
            Future<Boolean> first = executor.submit(() -> advisoryLockService.executeWithLock(lockName, 0, () -> {
                executions.incrementAndGet();
                firstEntered.countDown();
                await(releaseFirst);
            }));
            assertThat(firstEntered.await(3, TimeUnit.SECONDS)).isTrue();

            Future<Boolean> second = executor.submit(() -> advisoryLockService.executeWithLock(
                    lockName, 0, executions::incrementAndGet));
            assertThat(second.get(3, TimeUnit.SECONDS)).isFalse();

            releaseFirst.countDown();
            assertThat(first.get(3, TimeUnit.SECONDS)).isTrue();
            assertThat(executions).hasValue(1);

            assertThat(advisoryLockService.executeWithLock(lockName, 0, executions::incrementAndGet)).isTrue();
            assertThat(executions).hasValue(2);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test latch");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test latch", ex);
        }
    }
}
