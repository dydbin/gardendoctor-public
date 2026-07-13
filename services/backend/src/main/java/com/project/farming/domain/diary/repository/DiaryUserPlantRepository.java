package com.project.farming.domain.diary.repository;

import com.project.farming.domain.diary.entity.DiaryUserPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DiaryUserPlantRepository extends JpaRepository<DiaryUserPlant, Long> {
    List<DiaryUserPlant> findByDiaryId(Long diaryId);

    List<DiaryUserPlant> findByDiaryIdIn(List<Long> diaryIds);

    @Modifying
    @Query("DELETE FROM DiaryUserPlant dup WHERE dup.diaryId = :diaryId")
    int deleteByDiaryId(@Param("diaryId") Long diaryId);

}
