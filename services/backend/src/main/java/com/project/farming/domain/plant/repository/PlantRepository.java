package com.project.farming.domain.plant.repository;

import com.project.farming.domain.plant.dto.PlantResponse;
import com.project.farming.domain.plant.entity.Plant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlantRepository extends JpaRepository<Plant, Long> {
    @Query(value = "SELECT COUNT(*) > 0 FROM plant_info WHERE plant_name = :plantName", nativeQuery = true)
    boolean existsAnyByPlantName(@Param("plantName") String plantName);

    @Query(value = "SELECT COUNT(*) > 0 FROM plant_info WHERE plant_english_name = :plantName", nativeQuery = true)
    boolean existsAnyByPlantEnglishName(@Param("plantName") String plantName);

    @Query(value = "SELECT COUNT(*) FROM plant_info", nativeQuery = true)
    long countAllIncludingDeleted();

    @Query("""
        SELECT new com.project.farming.domain.plant.dto.PlantResponse(
            p.plantId,
            p.plantName,
            p.plantEnglishName,
            p.species,
            p.season,
            image.imageUrl
        )
        FROM Plant p
        LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId
        ORDER BY p.plantName ASC, p.plantId ASC
        """)
    Page<PlantResponse> findAllPlantResponsesByOrderByPlantNameAsc(Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.plant.dto.PlantResponse(
            p.plantId,
            p.plantName,
            p.plantEnglishName,
            p.species,
            p.season,
            image.imageUrl
        )
        FROM Plant p
        LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId
        WHERE p.plantName LIKE :keyword ESCAPE '!'
           OR p.plantEnglishName LIKE :keyword ESCAPE '!'
        ORDER BY p.plantName ASC
        """)
    Page<PlantResponse> findPlantResponsesByKeywordOrderByPlantNameAsc(
            @Param("keyword") String keyword, Pageable pageable);

    Optional<Plant> findByPlantId(Long plantId);

    @Query("""
        SELECT new com.project.farming.domain.plant.dto.PlantResponse(
            p.plantId,
            p.plantName,
            p.plantEnglishName,
            p.species,
            p.season,
            image.imageUrl
        )
        FROM Plant p
        LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId
        WHERE p.plantId = :plantId
        """)
    Optional<PlantResponse> findPlantResponseByPlantId(@Param("plantId") Long plantId);

    @Query("""
        SELECT new com.project.farming.domain.plant.dto.PlantResponse(
            p.plantId,
            p.plantName,
            p.plantEnglishName,
            p.species,
            p.season,
            image.imageUrl
        )
        FROM Plant p
        LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId
        ORDER BY p.plantId ASC
        """)
    Page<PlantResponse> findAllAdminResponsesByOrderByPlantIdAsc(Pageable pageable);

    @Query("""
        SELECT new com.project.farming.domain.plant.dto.PlantResponse(
            p.plantId,
            p.plantName,
            p.plantEnglishName,
            p.species,
            p.season,
            image.imageUrl
        )
        FROM Plant p
        LEFT JOIN ImageFile image ON image.imageFileId = p.plantImageFileId
        WHERE p.plantName LIKE :keyword ESCAPE '!'
           OR p.plantEnglishName LIKE :keyword ESCAPE '!'
        ORDER BY p.plantId ASC
        """)
    Page<PlantResponse> findAdminResponsesByKeywordOrderByPlantIdAsc(
            @Param("keyword") String keyword, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        SELECT p FROM Plant p
        WHERE p.plantName = :plantName
           OR p.plantEnglishName = :plantName
        ORDER BY CASE WHEN p.plantName = :plantName THEN 0 ELSE 1 END, p.plantId ASC
        """)
    List<Plant> findReferenceCandidatesForShare(@Param("plantName") String plantName);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT p FROM Plant p WHERE p.plantName = :plantName")
    Optional<Plant> findOtherPlantForShare(@Param("plantName") String plantName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plant p WHERE p.plantId = :plantId")
    Optional<Plant> findByPlantIdForUpdate(@Param("plantId") Long plantId);
}
