package com.project.farming.domain.chat.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatDeletionOutboxRepository extends JpaRepository<ChatDeletionOutbox, Long> {

    boolean existsByPythonSessionId(Long pythonSessionId);

    Optional<ChatDeletionOutbox> findByPythonSessionId(Long pythonSessionId);

    @Query("""
        SELECT o.chatDeletionOutboxId
        FROM ChatDeletionOutbox o
        WHERE o.status = :pending
          AND o.nextRetryAt <= :now
        ORDER BY o.chatDeletionOutboxId ASC
        """)
    List<Long> findDueIds(
            @Param("pending") ChatDeletionOutboxStatus pending,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ChatDeletionOutbox o
        SET o.status = :processing,
            o.lockedAt = :now,
            o.updatedAt = :now
        WHERE o.chatDeletionOutboxId = :outboxId
          AND o.status = :pending
          AND o.nextRetryAt <= :now
        """)
    int claim(
            @Param("outboxId") Long outboxId,
            @Param("pending") ChatDeletionOutboxStatus pending,
            @Param("processing") ChatDeletionOutboxStatus processing,
            @Param("now") LocalDateTime now);

    @Query("""
        SELECT o.pythonSessionId
        FROM ChatDeletionOutbox o
        WHERE o.chatDeletionOutboxId = :outboxId
          AND o.status = :processing
        """)
    Optional<Long> findClaimedPythonSessionId(
            @Param("outboxId") Long outboxId,
            @Param("processing") ChatDeletionOutboxStatus processing);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE ChatDeletionOutbox o
        SET o.status = :pending,
            o.lockedAt = null,
            o.nextRetryAt = :now,
            o.lastError = :reason,
            o.updatedAt = :now
        WHERE o.status = :processing
          AND o.lockedAt < :expiresBefore
        """)
    int requeueExpired(
            @Param("processing") ChatDeletionOutboxStatus processing,
            @Param("pending") ChatDeletionOutboxStatus pending,
            @Param("expiresBefore") LocalDateTime expiresBefore,
            @Param("now") LocalDateTime now,
            @Param("reason") String reason);
}
