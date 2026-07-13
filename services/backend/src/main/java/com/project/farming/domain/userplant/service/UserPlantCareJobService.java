package com.project.farming.domain.userplant.service;

import com.project.farming.domain.user.repository.UserRepository;
import com.project.farming.domain.userplant.repository.UserPlantCareTaskRow;
import com.project.farming.domain.userplant.repository.UserPlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserPlantCareJobService {

    private static final String DAILY_JOB = "daily";
    private static final String INCOMPLETE_JOB = "incomplete";
    private static final int MAX_MESSAGE_UTF8_BYTES = 3000;
    private static final String TRUNCATION_SUFFIX = "…";

    private final UserPlantRepository userPlantRepository;
    private final UserRepository userRepository;
    private final CareNotificationBatchWriter batchWriter;

    @Value("${app.userplant.care-scheduler.batch-size:1000}")
    private int batchSize;

    @Transactional
    public void resetDailyCareStatuses() {
        int updatedCount = userPlantRepository.resetDailyCareStatuses();
        log.info("userPlant 상태 초기화 완료: updatedCount={}", updatedCount);
    }

    public void sendDailyTasksNotification(LocalDate executionDate) {
        processTaskRows(executionDate, DAILY_JOB, false);
    }

    public void notifyIncompleteTasks(LocalDate executionDate) {
        processTaskRows(executionDate, INCOMPLETE_JOB, true);
    }

    private void processTaskRows(LocalDate executionDate, String jobType, boolean incompleteOnly) {
        long upperUserId = Math.max(0L, userRepository.findMaxActiveUserId());
        CareNotificationJobResult result = processTaskRows(
                executionDate,
                jobType,
                incompleteOnly,
                0L,
                upperUserId
        );
        log.info(
                "UserPlant care 알림 생성 완료: jobType={}, executionDate={}, processedUsers={}, "
                        + "createdOutboxes={}, failedUsers={}, batches={}, lastScannedUserId={}",
                jobType,
                executionDate,
                result.processedUsers(),
                result.createdOutboxes(),
                result.failedUsers(),
                result.batches(),
                result.lastScannedUserId()
        );
    }

    CareNotificationJobResult processTaskRows(
            LocalDate executionDate,
            String jobType,
            boolean incompleteOnly,
            long initialAfterUserId,
            long upperUserId) {
        long afterUserId = Math.max(0L, initialAfterUserId);
        int processedUsers = 0;
        int createdOutboxes = 0;
        int failedUsers = 0;
        int batches = 0;

        while (afterUserId < upperUserId) {
            PageRequest limit = PageRequest.of(0, Math.max(1, batchSize));
            List<Long> userIds = incompleteOnly
                    ? userPlantRepository.findIncompleteCareUserIdsAfter(
                            afterUserId, upperUserId, executionDate, limit)
                    : userPlantRepository.findDueCareUserIdsAfter(
                            afterUserId, upperUserId, executionDate, limit);
            if (userIds.isEmpty()) {
                break;
            }

            List<UserPlantCareTaskRow> rows = incompleteOnly
                    ? userPlantRepository.findIncompleteCareTaskRowsByUserIds(userIds, executionDate)
                    : userPlantRepository.findDueCareTaskRowsByUserIds(userIds, executionDate);
            List<CareNotificationPayload> payloads = aggregateByUser(
                    rows, jobType, executionDate, incompleteOnly);
            ChunkWriteResult writeResult = writeChunkResilient(payloads);
            createdOutboxes += writeResult.createdOutboxes();
            failedUsers += writeResult.failedUsers();
            processedUsers += userIds.size();
            afterUserId = userIds.get(userIds.size() - 1);
            batches++;
        }

        return new CareNotificationJobResult(
                processedUsers,
                createdOutboxes,
                failedUsers,
                batches,
                afterUserId
        );
    }

    private ChunkWriteResult writeChunkResilient(List<CareNotificationPayload> payloads) {
        if (payloads.isEmpty()) {
            return ChunkWriteResult.empty();
        }

        try {
            return new ChunkWriteResult(batchWriter.write(payloads), 0);
        } catch (DataIntegrityViolationException exception) {
            if (payloads.size() == 1) {
                CareNotificationPayload failed = payloads.get(0);
                log.error(
                        "UserPlant care 알림 영구 실패 격리: userId={}, eventKey={}",
                        failed.userId(),
                        failed.eventKey(),
                        exception
                );
                return new ChunkWriteResult(0, 1);
            }

            int midpoint = payloads.size() / 2;
            ChunkWriteResult left = writeChunkResilient(payloads.subList(0, midpoint));
            ChunkWriteResult right = writeChunkResilient(payloads.subList(midpoint, payloads.size()));
            return left.merge(right);
        }
    }

    private List<CareNotificationPayload> aggregateByUser(
            List<UserPlantCareTaskRow> rows,
            String jobType,
            LocalDate executionDate,
            boolean incompleteOnly) {
        Map<Long, List<UserPlantCareTaskRow>> rowsByUser = new LinkedHashMap<>();
        for (UserPlantCareTaskRow row : rows) {
            rowsByUser.computeIfAbsent(row.userId(), ignored -> new ArrayList<>()).add(row);
        }

        String title = incompleteOnly ? "⚠️오늘의 미완료 작업 알림" : "🪴오늘의 작업 알림";
        String qualifier = incompleteOnly ? "아직 완료하지 않은 작업" : "오늘 해야 할 작업";
        List<CareNotificationPayload> payloads = new ArrayList<>(rowsByUser.size());
        for (Map.Entry<Long, List<UserPlantCareTaskRow>> entry : rowsByUser.entrySet()) {
            String taskSummary = entry.getValue().stream()
                    .map(this::plantTaskSummary)
                    .collect(Collectors.joining("; "));
            String message = String.format(
                    "%s: %s. 잊지 말고 꼭 챙겨주세요!",
                    qualifier,
                    taskSummary
            );
            payloads.add(new CareNotificationPayload(
                    entry.getKey(),
                    eventKey(jobType, executionDate, entry.getKey()),
                    title,
                    truncateMessage(message)
            ));
        }
        return payloads;
    }

    private String plantTaskSummary(UserPlantCareTaskRow row) {
        return String.format(
                "%s(%s): %s",
                row.plantNickname(),
                row.plantName(),
                String.join(", ", taskLabels(row))
        );
    }

    private String eventKey(String jobType, LocalDate executionDate, Long userId) {
        return "userplant-care:" + jobType + ":" + executionDate + ":user:" + userId;
    }

    private String truncateMessage(String message) {
        if (utf8Length(message) <= MAX_MESSAGE_UTF8_BYTES) {
            return message;
        }

        int availableBytes = MAX_MESSAGE_UTF8_BYTES - utf8Length(TRUNCATION_SUFFIX);
        int usedBytes = 0;
        StringBuilder truncated = new StringBuilder();
        for (int offset = 0; offset < message.length();) {
            int codePoint = message.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));
            int characterBytes = utf8Length(character);
            if (usedBytes + characterBytes > availableBytes) {
                break;
            }
            truncated.appendCodePoint(codePoint);
            usedBytes += characterBytes;
            offset += Character.charCount(codePoint);
        }
        return truncated.append(TRUNCATION_SUFFIX).toString();
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private List<String> taskLabels(UserPlantCareTaskRow row) {
        List<String> tasks = new ArrayList<>(3);
        if (row.wateringDue()) {
            tasks.add("💧물 주기");
        }
        if (row.pruningDue()) {
            tasks.add("✂️가지치기");
        }
        if (row.fertilizingDue()) {
            tasks.add("💊영양제 주기");
        }
        return tasks;
    }

    record CareNotificationJobResult(
            int processedUsers,
            int createdOutboxes,
            int failedUsers,
            int batches,
            long lastScannedUserId) {
    }

    private record ChunkWriteResult(int createdOutboxes, int failedUsers) {

        private static ChunkWriteResult empty() {
            return new ChunkWriteResult(0, 0);
        }

        private ChunkWriteResult merge(ChunkWriteResult other) {
            return new ChunkWriteResult(
                    createdOutboxes + other.createdOutboxes,
                    failedUsers + other.failedUsers
            );
        }
    }
}
