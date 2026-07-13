package com.project.farming.domain.userplant.service;

import com.project.farming.global.scheduling.MySqlAdvisoryLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlantCareSchedulerTest {

    @Mock
    private UserPlantCareJobService jobService;
    @Mock
    private MySqlAdvisoryLockService advisoryLockService;

    private UserPlantCareScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new UserPlantCareScheduler(jobService, advisoryLockService);
        ReflectionTestUtils.setField(scheduler, "zone", "Asia/Seoul");
        ReflectionTestUtils.setField(scheduler, "lockWaitSeconds", 0);
    }

    @Test
    void resetShouldExecuteJobOnlyInsideAdvisoryLock() {
        executeLockAction();

        scheduler.initUserPlantStatus();

        verify(advisoryLockService).executeWithLock(
                eq("gardendoctor:userplant-care:reset"), eq(0), any(Runnable.class));
        verify(jobService).resetDailyCareStatuses();
    }

    @Test
    void dailyNotificationShouldUseConfiguredZoneDateInsideLock() {
        executeLockAction();

        scheduler.sendDailyTasksNotification();

        verify(jobService).sendDailyTasksNotification(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")));
    }

    @Test
    void losingInstanceShouldSkipJobBody() {
        when(advisoryLockService.executeWithLock(
                eq("gardendoctor:userplant-care:incomplete"), eq(0), any(Runnable.class)))
                .thenReturn(false);

        scheduler.notifyIncompleteTasks();

        verify(jobService, never()).notifyIncompleteTasks(any());
    }

    private void executeLockAction() {
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(2).run();
            return true;
        }).when(advisoryLockService).executeWithLock(any(), eq(0), any(Runnable.class));
    }
}
