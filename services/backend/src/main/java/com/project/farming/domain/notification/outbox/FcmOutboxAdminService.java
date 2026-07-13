package com.project.farming.domain.notification.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FcmOutboxAdminService {

    private static final int MAX_BULK_RETRY_SIZE = 500;

    private final FcmOutboxRepository fcmOutboxRepository;
    private final FcmOutboxRetryAuditRepository fcmOutboxRetryAuditRepository;

    @Transactional(readOnly = true)
    public Page<FcmOutboxResponse> getFailedOutboxes(Pageable pageable) {
        return getFailedOutboxes(FcmOutboxAdminFilter.empty(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<FcmOutboxResponse> getFailedOutboxes(FcmOutboxAdminFilter filter, Pageable pageable) {
        FcmOutboxAdminFilter safeFilter = filter == null ? FcmOutboxAdminFilter.empty() : filter;
        return fcmOutboxRepository.findAdminRowsByStatusAndFilters(
                        FcmOutboxStatus.FAILED,
                        safeFilter.sourceType(),
                        safeFilter.sourceId(),
                        safeFilter.userId(),
                        pageable)
                .map(FcmOutboxResponse::from);
    }

    @Transactional
    public void retryFailedOutbox(Long fcmOutboxId, Long adminUserId) {
        validateAdminUserId(adminUserId);
        LocalDateTime now = LocalDateTime.now();
        int updated = fcmOutboxRepository.retryFailedOutbox(
                fcmOutboxId,
                FcmOutboxStatus.FAILED,
                FcmOutboxStatus.PENDING,
                now);
        if (updated == 1) {
            fcmOutboxRetryAuditRepository.save(FcmOutboxRetryAudit.manualRetry(fcmOutboxId, adminUserId, now));
            return;
        }
        if (!fcmOutboxRepository.existsById(fcmOutboxId)) {
            throw new NoSuchElementException("FCM outbox를 찾을 수 없습니다: " + fcmOutboxId);
        }
        throw new IllegalArgumentException("실패 상태의 FCM outbox만 재시도할 수 있습니다.");
    }

    @Transactional
    public int retryFailedOutboxes(List<Long> fcmOutboxIds, Long adminUserId) {
        validateAdminUserId(adminUserId);
        List<Long> selectedIds = validateBulkRetryIds(fcmOutboxIds);
        selectedIds.forEach(fcmOutboxId -> retryFailedOutbox(fcmOutboxId, adminUserId));
        return selectedIds.size();
    }

    private void validateAdminUserId(Long adminUserId) {
        if (adminUserId == null) {
            throw new IllegalArgumentException("adminUserId must not be null");
        }
    }

    private List<Long> validateBulkRetryIds(List<Long> fcmOutboxIds) {
        if (fcmOutboxIds == null || fcmOutboxIds.isEmpty()) {
            throw new IllegalArgumentException("재시도할 FCM outbox ID를 선택해야 합니다.");
        }
        if (fcmOutboxIds.size() > MAX_BULK_RETRY_SIZE) {
            throw new IllegalArgumentException("한 번에 재시도할 수 있는 FCM outbox는 최대 " + MAX_BULK_RETRY_SIZE + "건입니다.");
        }
        if (fcmOutboxIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("FCM outbox ID는 양수여야 합니다.");
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(fcmOutboxIds);
        if (uniqueIds.size() != fcmOutboxIds.size()) {
            throw new IllegalArgumentException("중복된 FCM outbox ID는 재시도할 수 없습니다.");
        }
        return List.copyOf(uniqueIds);
    }
}
