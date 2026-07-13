package com.project.farming.domain.userplant.service;

import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.userplant.repository.UserPlantCareTaskRow;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlantCareJobServiceTest {

    @Mock
    private UserPlantRepository userPlantRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CareNotificationBatchWriter batchWriter;

    private UserPlantCareJobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new UserPlantCareJobService(
                userPlantRepository,
                userRepository,
                batchWriter
        );
        ReflectionTestUtils.setField(jobService, "batchSize", 2);
    }

    @Test
    void resetShouldUseOneBulkUpdate() {
        when(userPlantRepository.resetDailyCareStatuses()).thenReturn(42);

        jobService.resetDailyCareStatuses();

        verify(userPlantRepository).resetDailyCareStatuses();
        verify(userPlantRepository, never()).findAllByDeletedFalse();
        verify(userPlantRepository, never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dailyTaskProjectionShouldMergeMultiplePlantsIntoOneUserNotification() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        UserPlantCareTaskRow tomato = new UserPlantCareTaskRow(
                11L, 7L, "토마토", "방울이", true, true, false);
        UserPlantCareTaskRow cactus = new UserPlantCareTaskRow(
                12L, 7L, "선인장", "뾰족이", false, false, true);
        PageRequest limit = PageRequest.of(0, 2);
        when(userRepository.findMaxActiveUserId()).thenReturn(7L);
        when(userPlantRepository.findDueCareUserIdsAfter(0L, 7L, executionDate, limit))
                .thenReturn(List.of(7L));
        when(userPlantRepository.findDueCareTaskRowsByUserIds(List.of(7L), executionDate))
                .thenReturn(List.of(tomato, cactus));
        when(batchWriter.write(anyList())).thenReturn(1);

        jobService.sendDailyTasksNotification(executionDate);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<CareNotificationPayload>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(batchWriter).write(captor.capture());
        assertThat(captor.getValue())
                .singleElement()
                .satisfies(payload -> {
                    assertThat(payload.userId()).isEqualTo(7L);
                    assertThat(payload.eventKey()).isEqualTo("userplant-care:daily:2026-07-10:user:7");
                    assertThat(payload.title()).isEqualTo("🪴오늘의 작업 알림");
                    assertThat(payload.message())
                            .contains("방울이(토마토): 💧물 주기, ✂️가지치기")
                            .contains("뾰족이(선인장): 💊영양제 주기");
                });
    }

    @Test
    void keysetShouldAdvanceWithLastUserIdInsteadOfOffsetPage() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        PageRequest limit = PageRequest.of(0, 2);
        when(userRepository.findMaxActiveUserId()).thenReturn(9L);
        when(userPlantRepository.findIncompleteCareUserIdsAfter(0L, 9L, executionDate, limit))
                .thenReturn(List.of(2L, 5L));
        when(userPlantRepository.findIncompleteCareUserIdsAfter(5L, 9L, executionDate, limit))
                .thenReturn(List.of(9L));
        when(userPlantRepository.findIncompleteCareTaskRowsByUserIds(anyList(), eq(executionDate)))
                .thenReturn(List.of());

        jobService.notifyIncompleteTasks(executionDate);

        verify(userPlantRepository).findIncompleteCareUserIdsAfter(0L, 9L, executionDate, limit);
        verify(userPlantRepository).findIncompleteCareUserIdsAfter(5L, 9L, executionDate, limit);
        verify(userPlantRepository, times(2))
                .findIncompleteCareTaskRowsByUserIds(anyList(), eq(executionDate));
        verify(batchWriter, never()).write(anyList());
    }

    @Test
    void aggregatedMessageShouldStayWithinFcmPayloadBudget() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        PageRequest limit = PageRequest.of(0, 2);
        List<UserPlantCareTaskRow> rows = IntStream.range(0, 30)
                .mapToObj(index -> new UserPlantCareTaskRow(
                        (long) index,
                        7L,
                        "매우긴식물이름".repeat(8),
                        "매우긴별명".repeat(8),
                        true,
                        true,
                        true
                ))
                .toList();
        when(userRepository.findMaxActiveUserId()).thenReturn(7L);
        when(userPlantRepository.findDueCareUserIdsAfter(0L, 7L, executionDate, limit))
                .thenReturn(List.of(7L));
        when(userPlantRepository.findDueCareTaskRowsByUserIds(List.of(7L), executionDate))
                .thenReturn(rows);

        jobService.sendDailyTasksNotification(executionDate);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<CareNotificationPayload>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(batchWriter).write(captor.capture());
        String message = captor.getValue().get(0).message();
        assertThat(message.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(3000);
        assertThat(message).endsWith("…");
    }

    @Test
    void oneInvalidUserShouldBeIsolatedWithoutRollingBackValidUsers() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        PageRequest limit = PageRequest.of(0, 2);
        when(userPlantRepository.findDueCareUserIdsAfter(0L, 9L, executionDate, limit))
                .thenReturn(List.of(2L, 5L));
        when(userPlantRepository.findDueCareUserIdsAfter(5L, 9L, executionDate, limit))
                .thenReturn(List.of(9L));
        when(userPlantRepository.findDueCareTaskRowsByUserIds(anyList(), eq(executionDate)))
                .thenAnswer(invocation -> invocation.<List<Long>>getArgument(0).stream()
                        .map(this::careTask)
                        .toList());
        when(batchWriter.write(anyList())).thenAnswer(invocation -> {
            List<CareNotificationPayload> payloads = invocation.getArgument(0);
            if (payloads.stream().anyMatch(payload -> payload.userId().equals(5L))) {
                throw new DataIntegrityViolationException("invalid user 5");
            }
            return payloads.size();
        });

        UserPlantCareJobService.CareNotificationJobResult result = jobService.processTaskRows(
                executionDate, "daily", false, 0L, 9L);

        assertThat(result.processedUsers()).isEqualTo(3);
        assertThat(result.createdOutboxes()).isEqualTo(2);
        assertThat(result.failedUsers()).isEqualTo(1);
        assertThat(result.batches()).isEqualTo(2);
        assertThat(result.lastScannedUserId()).isEqualTo(9L);
    }

    @Test
    void infrastructureFailureShouldAbortInsteadOfBeingReportedAsPermanentUserFailure() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        PageRequest limit = PageRequest.of(0, 2);
        when(userPlantRepository.findDueCareUserIdsAfter(0L, 7L, executionDate, limit))
                .thenReturn(List.of(7L));
        when(userPlantRepository.findDueCareTaskRowsByUserIds(List.of(7L), executionDate))
                .thenReturn(List.of(careTask(7L)));
        when(batchWriter.write(anyList()))
                .thenThrow(new TransientDataAccessResourceException("database unavailable"));

        assertThatThrownBy(() -> jobService.processTaskRows(
                executionDate, "daily", false, 0L, 7L))
                .isInstanceOf(TransientDataAccessResourceException.class);
    }

    @Test
    void completedChunkCursorShouldBeUsableAsRestartPointWithoutDuplicateScan() {
        LocalDate executionDate = LocalDate.of(2026, 7, 10);
        PageRequest limit = PageRequest.of(0, 2);
        when(userPlantRepository.findDueCareUserIdsAfter(0L, 5L, executionDate, limit))
                .thenReturn(List.of(2L, 5L));
        when(userPlantRepository.findDueCareUserIdsAfter(5L, 9L, executionDate, limit))
                .thenReturn(List.of(9L));
        when(userPlantRepository.findDueCareTaskRowsByUserIds(anyList(), eq(executionDate)))
                .thenAnswer(invocation -> invocation.<List<Long>>getArgument(0).stream()
                        .map(this::careTask)
                        .toList());
        when(batchWriter.write(anyList())).thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());

        UserPlantCareJobService.CareNotificationJobResult first = jobService.processTaskRows(
                executionDate, "daily", false, 0L, 5L);
        UserPlantCareJobService.CareNotificationJobResult resumed = jobService.processTaskRows(
                executionDate, "daily", false, first.lastScannedUserId(), 9L);

        assertThat(first.lastScannedUserId()).isEqualTo(5L);
        assertThat(resumed.processedUsers()).isEqualTo(1);
        assertThat(resumed.lastScannedUserId()).isEqualTo(9L);
        verify(userPlantRepository, times(1))
                .findDueCareUserIdsAfter(0L, 5L, executionDate, limit);
        verify(userPlantRepository, times(1))
                .findDueCareUserIdsAfter(5L, 9L, executionDate, limit);
    }

    private UserPlantCareTaskRow careTask(Long userId) {
        return new UserPlantCareTaskRow(
                userId,
                userId,
                "몬스테라",
                "plant-" + userId,
                true,
                false,
                false
        );
    }
}
