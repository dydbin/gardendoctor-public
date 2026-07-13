package com.project.farming.domain.userplant.service;

import com.project.farming.global.scheduling.MySqlAdvisoryLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        prefix = "app.userplant.care-scheduler",
        name = "enabled",
        havingValue = "true"
)
public class UserPlantCareScheduler {

    private static final String LOCK_PREFIX = "gardendoctor:userplant-care:";

    private final UserPlantCareJobService jobService;
    private final MySqlAdvisoryLockService advisoryLockService;

    @Value("${app.userplant.care-scheduler.zone:Asia/Seoul}")
    private String zone;

    @Value("${app.userplant.care-scheduler.lock-wait-seconds:0}")
    private int lockWaitSeconds;

    @Scheduled(
            cron = "${app.userplant.care-scheduler.reset-cron:0 0 0 * * *}",
            zone = "${app.userplant.care-scheduler.zone:Asia/Seoul}"
    )
    public void initUserPlantStatus() {
        runWithLock("reset", jobService::resetDailyCareStatuses);
    }

    @Scheduled(
            cron = "${app.userplant.care-scheduler.daily-cron:0 0 9 * * *}",
            zone = "${app.userplant.care-scheduler.zone:Asia/Seoul}"
    )
    public void sendDailyTasksNotification() {
        LocalDate executionDate = currentDate();
        runWithLock("daily", () -> jobService.sendDailyTasksNotification(executionDate));
    }

    @Scheduled(
            cron = "${app.userplant.care-scheduler.incomplete-cron:0 0 17 * * *}",
            zone = "${app.userplant.care-scheduler.zone:Asia/Seoul}"
    )
    public void notifyIncompleteTasks() {
        LocalDate executionDate = currentDate();
        runWithLock("incomplete", () -> jobService.notifyIncompleteTasks(executionDate));
    }

    private void runWithLock(String jobName, Runnable action) {
        boolean executed = advisoryLockService.executeWithLock(
                LOCK_PREFIX + jobName,
                lockWaitSeconds,
                action
        );
        if (!executed) {
            log.info("다른 인스턴스가 UserPlant care scheduler를 실행 중입니다. job={}", jobName);
        }
    }

    private LocalDate currentDate() {
        return LocalDate.now(ZoneId.of(zone));
    }
}
