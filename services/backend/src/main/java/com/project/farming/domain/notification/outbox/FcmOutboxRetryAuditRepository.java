package com.project.farming.domain.notification.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FcmOutboxRetryAuditRepository extends JpaRepository<FcmOutboxRetryAudit, Long> {

    List<FcmOutboxRetryAudit> findByFcmOutboxIdOrderByCreatedAtDesc(Long fcmOutboxId);
}
