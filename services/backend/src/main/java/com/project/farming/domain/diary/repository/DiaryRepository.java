package com.project.farming.domain.diary.repository;

import com.project.farming.domain.diary.entity.Diary;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DiaryRepository extends JpaRepository<Diary, Long> {
    Optional<Diary> findByDiaryIdAndUserId(Long diaryId, Long userId);

    // 특정 사용자의 모든 일지를 최신 생성일 기준으로 내림차순 정렬하여 조회
    Slice<Diary> findByUserIdOrderByCreatedAtDescDiaryIdDesc(Long userId, Pageable pageable);

    @Query(value = """
            SELECT d.*
            FROM diaries d
            WHERE d.user_id = :userId
              AND (
                   d.created_at < :cursorCreatedAt
                   OR (d.created_at = :cursorCreatedAt AND d.diary_id < :cursorDiaryId)
              )
            ORDER BY d.created_at DESC, d.diary_id DESC
            """, nativeQuery = true)
    Slice<Diary> findByUserIdBeforeCursor(
            @Param("userId") Long userId,
            @Param("cursorCreatedAt") LocalDateTime cursorCreatedAt,
            @Param("cursorDiaryId") Long cursorDiaryId,
            Pageable pageable);

    // 특정 사용자의 특정 기간 동안의 일지를 생성일 기준으로 오름차순 정렬하여 조회
    Slice<Diary> findByUserIdAndDiaryDateBetweenOrderByDiaryDateAscDiaryIdAsc(
            Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // 특정 사용자의 특정 UserPlant에 연결된 일지를 최신 생성일 기준으로 내림차순 정렬하여 조회
    @Query("""
            SELECT d FROM Diary d
            WHERE d.userId = :userId
              AND d.diaryId IN (
                  SELECT dup.diaryId FROM DiaryUserPlant dup
                  WHERE dup.userPlantId = :userPlantId
              )
            ORDER BY d.createdAt DESC, d.diaryId DESC
            """)
    Slice<Diary> findByUserIdAndUserPlantId(
            @Param("userId") Long userId,
            @Param("userPlantId") Long userPlantId,
            Pageable pageable);

    // 특정 사용자의 여러 UserPlant 중 하나라도 연결된 일지를 최신 생성일 기준으로 내림차순 정렬하여 조회
    @Query("""
            SELECT d FROM Diary d
            WHERE d.userId = :userId
              AND d.diaryId IN (
                  SELECT dup.diaryId FROM DiaryUserPlant dup
                  WHERE dup.userPlantId IN :userPlantIds
              )
            ORDER BY d.createdAt DESC, d.diaryId DESC
            """)
    Slice<Diary> findByUserIdAndUserPlantIds(
            @Param("userId") Long userId,
            @Param("userPlantIds") List<Long> userPlantIds,
            Pageable pageable);
}
